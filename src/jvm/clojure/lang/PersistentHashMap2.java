/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.lang;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/*
 A persistent rendition of Phil Bagwell's Hash Array Mapped Trie

 Uses path copying for persistence
 HashCollision leaves vs. extended hashing
 Node polymorphism vs. conditionals
 No sub-tree pools or root-resizing
 Any errors are my own
 */

public class PersistentHashMap2 extends APersistentMap implements IEditableCollection, IObj, IMapIterable, IKVReduce {

final int count;
final INode root;
final IPersistentMap _meta;

final public static PersistentHashMap2 EMPTY = new PersistentHashMap2(0, null);
final private static Object NOT_FOUND = new Object();

static public IPersistentMap create(Map other){
	ITransientMap ret = EMPTY.asTransient();
	for(Object o : other.entrySet())
		{
		Map.Entry e = (Entry) o;
		ret = ret.assoc(e.getKey(), e.getValue());
		}
	return ret.persistent();
}

/*
 * @param init {key1,val1,key2,val2,...}
 */
public static PersistentHashMap2 create(Object... init){
	ITransientMap ret = EMPTY.asTransient();
	for(int i = 0; i < init.length; i += 2)
		{
		ret = ret.assoc(init[i], init[i + 1]);
		}
	return (PersistentHashMap2) ret.persistent();
}

public static PersistentHashMap2 createWithCheck(Object... init){
	ITransientMap ret = EMPTY.asTransient();
	for(int i = 0; i < init.length; i += 2)
		{
		ret = ret.assoc(init[i], init[i + 1]);
		if(ret.count() != i/2 + 1)
			throw new IllegalArgumentException("Duplicate key: " + init[i]);
		}
	return (PersistentHashMap2) ret.persistent();
}

static public PersistentHashMap2 create(ISeq items){
	ITransientMap ret = EMPTY.asTransient();
	for(; items != null; items = items.next().next())
		{
		if(items.next() == null)
			throw new IllegalArgumentException(String.format("No value supplied for key: %s", items.first()));
		ret = ret.assoc(items.first(), RT.second(items));
		}
	return (PersistentHashMap2) ret.persistent();
}

static public PersistentHashMap2 createWithCheck(ISeq items){
	ITransientMap ret = EMPTY.asTransient();
	for(int i=0; items != null; items = items.next().next(), ++i)
		{
		if(items.next() == null)
			throw new IllegalArgumentException(String.format("No value supplied for key: %s", items.first()));
		ret = ret.assoc(items.first(), RT.second(items));
		if(ret.count() != i + 1)
			throw new IllegalArgumentException("Duplicate key: " + items.first());
		}
	return (PersistentHashMap2) ret.persistent();
}

/*
 * @param init {key1,val1,key2,val2,...}
 */
public static PersistentHashMap2 create(IPersistentMap meta, Object... init){
	return create(init).withMeta(meta);
}

PersistentHashMap2(int count, INode root){
	this.count = count;
	this.root = root;
	this._meta = null;
}

public PersistentHashMap2(IPersistentMap meta, int count, INode root){
	this._meta = meta;
	this.count = count;
	this.root = root;
}

static int hash(Object k){
	return Util.hasheq(k);
}

public boolean containsKey(Object key){
	return (root != null) && root.find(0, hash(key), key, NOT_FOUND) != NOT_FOUND;
}

public IMapEntry entryAt(Object key){
	return (root != null) ? root.find(0, hash(key), key) : null;
}

public IPersistentMap assoc(Object key, Object val){
	Box addedLeaf = new Box(null);
	INode newroot = (root == null ? BitmapIndexedNode.EMPTY : root) 
			.assoc(0, hash(key), key, val, addedLeaf);
	if(newroot == root)
		return this;
	return new PersistentHashMap2(meta(), addedLeaf.val == null ? count : count + 1, newroot);
}

public Object valAt(Object key, Object notFound){
	return root != null ? root.find(0, hash(key), key, notFound) : notFound;
}

public Object valAt(Object key){
	return valAt(key, null);
}

public IPersistentMap assocEx(Object key, Object val) {
	if(containsKey(key))
		throw Util.runtimeException("Key already present");
	return assoc(key, val);
}

public IPersistentMap without(Object key){
	if(root == null)
		return this;
	INode newroot = root.without(0, hash(key), key, new Box(null));
	if(newroot == root)
		return this;
	return new PersistentHashMap2(meta(), count - 1, newroot);
}

static final Iterator EMPTY_ITER = new Iterator(){
    public boolean hasNext(){
        return false;
    }

    public Object next(){
        throw new NoSuchElementException();
    }

    public void remove(){
        throw new UnsupportedOperationException();
    }
};

private Iterator iterator(final IFn f){
    return (root == null) ? EMPTY_ITER : root.iterator(f);
}

public Iterator iterator(){
    return iterator(APersistentMap.MAKE_ENTRY);
}

public Iterator keyIterator(){
    return iterator(APersistentMap.MAKE_KEY);
}

public Iterator valIterator(){
    return iterator(APersistentMap.MAKE_VAL);
}

public Object kvreduce(IFn f, Object init){
	if(root != null){
		init = root.kvreduce(f,init);
		if(RT.isReduced(init))
			return ((IDeref)init).deref();
		else
			return init;
	}
	return init;
}

public Object fold(long n, final IFn combinef, final IFn reducef,
                   IFn fjinvoke, final IFn fjtask, final IFn fjfork, final IFn fjjoin){
	//we are ignoring n for now
	Callable top = new Callable(){
		public Object call() throws Exception{
			Object ret = combinef.invoke();
			return (root != null) ?
				combinef.invoke(ret, root.fold(combinef,reducef,fjtask,fjfork,fjjoin))
					: null;
		}
	};
	return fjinvoke.invoke(top);
}

public int count(){
	return count;
}

public ISeq seq(){
	return root != null ? root.nodeSeq() : null;
}

public IPersistentCollection empty(){
	return EMPTY.withMeta(meta());	
}

static int mask(int hash, int shift){
	//return ((hash << shift) >>> 27);// & 0x01f;
	return (hash >>> shift) & 0x01f;
}

public PersistentHashMap2 withMeta(IPersistentMap meta){
	return new PersistentHashMap2(meta, count, root);
}

public TransientHashMap asTransient() {
	return new TransientHashMap(this);
}

public IPersistentMap meta(){
	return _meta;
}

static final class TransientHashMap extends ATransientMap {
	final AtomicReference<Thread> edit;
	volatile INode root;
	volatile int count;
	final Box leafFlag = new Box(null);


	TransientHashMap(PersistentHashMap2 m) {
		this(new AtomicReference<Thread>(Thread.currentThread()), m.root, m.count);
	}
	
	TransientHashMap(AtomicReference<Thread> edit, INode root, int count) {
		this.edit = edit;
		this.root = root; 
		this.count = count; 
	}

	ITransientMap doAssoc(Object key, Object val) {
//		Box leafFlag = new Box(null);
		leafFlag.val = null;
		INode n = (root == null ? BitmapIndexedNode.EMPTY : root)
			.assoc(edit, 0, hash(key), key, val, leafFlag);
		if (n != this.root)
			this.root = n; 
		if(leafFlag.val != null) this.count++;
		return this;
	}

	ITransientMap doWithout(Object key) {
		if (root == null) return this;
//		Box leafFlag = new Box(null);
		leafFlag.val = null;
		INode n = root.without(edit, 0, hash(key), key, leafFlag);
		if (n != root)
			this.root = n;
		if(leafFlag.val != null) this.count--;
		return this;
	}

	IPersistentMap doPersistent() {
		edit.set(null);
		return new PersistentHashMap2(count, root);
	}

	Object doValAt(Object key, Object notFound) {
		if (root == null)
			return notFound;
		return root.find(0, hash(key), key, notFound);
	}

	int doCount() {
		return count;
	}
	
	void ensureEditable(){
		if(edit.get() == null)
			throw new IllegalAccessError("Transient used after persistent! call");
	}
}

interface INode extends Serializable {
	INode assoc(int shift, int hash, Object key, Object val, Box addedLeaf);

	INode without(int shift, int hash, Object key, Box removedLeaf);

	IMapEntry find(int shift, int hash, Object key);

	Object find(int shift, int hash, Object key, Object notFound);

	ISeq nodeSeq();

	INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box addedLeaf);

	INode without(AtomicReference<Thread> edit, int shift, int hash, Object key, Box removedLeaf);

    Object kvreduce(IFn f, Object init);

	Object fold(IFn combinef, IFn reducef, IFn fjtask, IFn fjfork, IFn fjjoin);

    // returns the result of (f [k v]) for each iterated element
    Iterator iterator(IFn f);

	Object getKey(int idx);
    Object getValue(int idx);

	byte sizePredicate(); // helper to avoid instanceof when compacting upon deletion
}

final static class BitmapIndexedNode implements INode{
	static final BitmapIndexedNode EMPTY = new BitmapIndexedNode(null, 0, 0, new Object[0]);

	int datamap;
	int nodemap;
	Object[] array;
	final AtomicReference<Thread> edit;

	static int index(int bitmap, int bit){
		return Integer.bitCount(bitmap & (bit - 1));
	}

	BitmapIndexedNode(AtomicReference<Thread> edit, int datamap, int nodemap, Object[] array){
		this.datamap = datamap;
		this.nodemap = nodemap;
		this.array = array;
		this.edit = edit;
	}

	// this removes a K+V, then adds the INode, net loss 1
	private Object[] copyKVasNode(int bit, INode node) {
		int oldKeyIdx = index(datamap, bit)*2;
		int newNodeIdx = array.length - 2 - index(nodemap, bit);

		Object[] dst = new Object[array.length-1];
		System.arraycopy(array, 0, dst, 0, oldKeyIdx);
		System.arraycopy(array, oldKeyIdx + 2, dst, oldKeyIdx, newNodeIdx - oldKeyIdx);
		dst[newNodeIdx] = node;
		System.arraycopy(array, newNodeIdx + 2, dst, newNodeIdx + 1, array.length - newNodeIdx - 2);

		return dst;
	}

	private INode inlineSingleEntryNode(int bit, INode node) {
			final Object[] src = this.array;
			final Object[] dst = new Object[src.length + 1];

			int idxOld = src.length - 1 - index(nodemap, bit);
		    int idxNew = index(datamap, bit) * 2;

			// copy 'src' and remove 1 element(s) at position 'idxOld' and
			// insert 2 element(s) at position 'idxNew' (TODO: carefully test)
			System.arraycopy(src, 0, dst, 0, idxNew);
			dst[idxNew] = node.getKey(0);
			dst[idxNew+1] = node.getValue(0);
			System.arraycopy(src, idxNew, dst, idxNew + 2, idxOld - idxNew);
			System.arraycopy(src, idxOld + 1, dst, idxOld + 2, src.length - idxOld - 1);

			return new BitmapIndexedNode(edit, datamap | bit, nodemap ^ bit, dst);

	}

	private INode growKV(int bit, Object key, Object val) {
		Object[] src = array;
		Object[] dst = new Object[src.length + 2];
		int newIdx = index(datamap, bit)*2;

		System.arraycopy(src, 0, dst, 0, newIdx);
		dst[newIdx] = key;
		dst[newIdx+1] = val;
		System.arraycopy(src, newIdx, dst, newIdx + 2, src.length - newIdx);

		return new BitmapIndexedNode(edit, datamap | bit, nodemap, dst);
	}

	private INode copyAndRemoveValue(int bit) {
		Object[] src = array;
		Object[] dst = new Object[src.length];

		int itemIdx = index(datamap, bit) * 2;

		System.arraycopy(src, 0, dst, 0, itemIdx);
		System.arraycopy(src, itemIdx + 2, dst, itemIdx, src.length - 2 - itemIdx);

		return new BitmapIndexedNode(edit, datamap ^ bit, nodemap, dst);
	}

	public byte sizePredicate() {
		if (Integer.bitCount(nodemap) == 0) {
			switch(Integer.bitCount(datamap)) {
				case 0: return 0;
				case 1: return 1;
				default: return 2;
			}
		} else {
			return 2;
		}
	}

	public INode assoc(int shift, int hash, Object key, Object val, Box addedLeaf){
		int bit = bitpos(hash, shift);
		if ((datamap & bit) != 0) {
			int idx = index(datamap, bit);
			Object foundKey = array[2*idx];
			Object foundVal = array[2*idx+1];

			if (Util.equiv(key, foundKey)) {
				if(val == foundVal)
					return this;
				return new BitmapIndexedNode(null, datamap, nodemap, cloneAndSet(array, 2*idx+1, val));
			} else {
				addedLeaf.val = addedLeaf;
				// push the found kv down a node
				INode joinedNode = createNode(shift + 5, foundKey, foundVal, hash, key, val);
				return new BitmapIndexedNode(null, datamap ^ bit, nodemap | bit, copyKVasNode(bit, joinedNode));
			}
		}
		if ((nodemap & bit) != 0) {
			int idx = index(nodemap, bit);
			INode n = (INode) array[array.length - idx - 1];  // count from right
			n = n.assoc(shift  + 5, hash, key, val, addedLeaf);
			if (addedLeaf.val != null)
				return this;
			return n;
		}

		// else
		addedLeaf.val = addedLeaf;
		return growKV(bit, key, val);
	}

	public INode without(int shift, int hash, Object key, Box removedLeaf){
		int bit = bitpos(hash, shift);
		if ((datamap & bit) != 0) {
			int idx = index(datamap, bit);
			if(Util.equiv(key, array[2*idx])) {
				removedLeaf.val = removedLeaf;
				if (Integer.bitCount(datamap) == 2 && Integer.bitCount(nodemap) == 0) {
					// one pair will be left. will either become new root or unwrapped/inlined
					int newDatamap = (shift == 0) ? (int) (datamap ^ bit)
							: bitpos(hash, 0);
					if (idx == 0) {
						return new BitmapIndexedNode(null, newDatamap, 0, new Object[]{array[2], array[3]});
					} else {
						return new BitmapIndexedNode(null, newDatamap, 0, new Object[]{array[0], array[1]});
					}
				} else {
					return copyAndRemoveValue(bit);
				}
			} else {
				return this;
			}
		}
		if ((nodemap & bit) != 0) {
			INode subnode = (INode) array[array.length - 1 - index(nodemap, bit)];
			INode subnodeNew = subnode.without(shift+5,hash,key,removedLeaf);

			if (removedLeaf.val == null)
				return this;

			switch(subnodeNew.sizePredicate()) {
				case 0:
					throw new IllegalStateException("subnode must have at least one element");
				case 1: {
					if (Integer.bitCount(datamap) == 0 && Integer.bitCount(nodemap) == 1) {
						// escalate (singleton or empty) result
						return subnodeNew;
					} else {
						// inline value (move to front)
						return inlineSingleEntryNode(bit, subnodeNew);
					}
				}
				case 2: {
					return new BitmapIndexedNode(edit, datamap, nodemap,
							cloneAndSet(array, array.length - 1 - index(nodemap, bit), subnodeNew)
					);
				}
			}

		}
		return this;
	}
	
	public IMapEntry find(int shift, int hash, Object key){
		int bit = bitpos(hash, shift);
		if ((datamap & bit) != 0) {
			int idx = index(datamap, bit);
			Object k = array[2*idx];
			Object v = array[2*idx+1];
			if(Util.equiv(key, k))
				return MapEntry.create(k, v);
		}
		if ((nodemap & bit) != 0) {
			int idx = index(nodemap, bit);
			INode n = (INode) array[array.length - 1 - idx];
			return n.find(shift+5,hash,key);
		}
		return null;
	}

	public Object find(int shift, int hash, Object key, Object notFound){
		int bit = bitpos(hash, shift);
		if ((datamap & bit) != 0) {
			int idx = index(datamap, bit);
			Object k = array[2*idx];
			Object v = array[2*idx+1];
			if(Util.equiv(key, k))
				return v;
		}
		if ((nodemap & bit) != 0) {
			int idx = index(nodemap, bit);
			INode n = (INode) array[array.length - 1 - idx];
			return n.find(shift+5,hash,key,notFound);
		}
		return notFound;
	}

	public ISeq nodeSeq(){
		return NodeSeq.create(array, Integer.bitCount(datamap) * 2	);
	}

    public Iterator iterator(IFn f){
        return new NodeIter(array, f);
    }

    public Object kvreduce(IFn f, Object init){
         return NodeSeq.kvreduce(array,f,init);
    }

	public Object fold(IFn combinef, IFn reducef, IFn fjtask, IFn fjfork, IFn fjjoin){
		return NodeSeq.kvreduce(array, reducef, combinef.invoke());
	}

	private BitmapIndexedNode ensureEditable(AtomicReference<Thread> edit){
		return null;
//		if(this.edit == edit)
//			return this;
//		int n = Integer.bitCount(bitmap);
//		Object[] newArray = new Object[n >= 0 ? 2*(n+1) : 4]; // make room for next assoc
//		System.arraycopy(array, 0, newArray, 0, 2*n);
//		return new BitmapIndexedNode(edit, bitmap, newArray);

	}
	
	private BitmapIndexedNode editAndSet(AtomicReference<Thread> edit, int i, Object a) {
		return null;
//		BitmapIndexedNode editable = ensureEditable(edit);
//		editable.array[i] = a;
//		return editable;
	}

	private BitmapIndexedNode editAndSet(AtomicReference<Thread> edit, int i, Object a, int j, Object b) {
		return null;
//		BitmapIndexedNode editable = ensureEditable(edit);
//		editable.array[i] = a;
//		editable.array[j] = b;
//		return editable;
	}

	private BitmapIndexedNode editAndRemovePair(AtomicReference<Thread> edit, int bit, int i) {
		return null;
//		if (bitmap == bit)
//			return null;
//		BitmapIndexedNode editable = ensureEditable(edit);
//		editable.bitmap ^= bit;
//		System.arraycopy(editable.array, 2*(i+1), editable.array, 2*i, editable.array.length - 2*(i+1));
//		editable.array[editable.array.length - 2] = null;
//		editable.array[editable.array.length - 1] = null;
//		return editable;
	}

	public INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box addedLeaf){
		return null;
	}

	public INode without(AtomicReference<Thread> edit, int shift, int hash, Object key, Box removedLeaf) {
		return null;
	}

	public Object getKey(int idx) {
		return array[2*idx];
	}

	public Object getValue(int idx) {
		return array[2*idx+1];
	}

}

final static class HashCollisionNode implements INode{

	final int hash;
	int count;
	Object[] array;
	final AtomicReference<Thread> edit;

	HashCollisionNode(AtomicReference<Thread> edit, int hash, int count, Object... array){
		this.edit = edit;
		this.hash = hash;
		this.count = count;
		this.array = array;
	}

	public INode assoc(int shift, int hash, Object key, Object val, Box addedLeaf){
		if(hash == this.hash) {
			int idx = findIndex(key);
			if(idx != -1) {
				if(array[idx + 1] == val)
					return this;
				return new HashCollisionNode(null, hash, count, cloneAndSet(array, idx + 1, val));
			}
			Object[] newArray = new Object[2 * (count + 1)];
			System.arraycopy(array, 0, newArray, 0, 2 * count);
			newArray[2 * count] = key;
			newArray[2 * count + 1] = val;
			addedLeaf.val = addedLeaf;
			return new HashCollisionNode(edit, hash, count + 1, newArray);
		}
		// nest it in a bitmap node
		return new BitmapIndexedNode(null, 0, bitpos(this.hash, shift) , new Object[] {this})
			.assoc(shift, hash, key, val, addedLeaf);
	}

	// TODO
	public INode without(int shift, int hash, Object key, Box removedLeaf){
		int idx = findIndex(key);
		if(idx == -1)
			return this;
		if(count == 1)
			return null;
		return new HashCollisionNode(null, hash, count - 1, removePair(array, idx/2));
	}

	public IMapEntry find(int shift, int hash, Object key){
		int idx = findIndex(key);
		if(idx < 0)
			return null;
		if(Util.equiv(key, array[idx]))
			return (IMapEntry) MapEntry.create(array[idx], array[idx+1]);
		return null;
	}

	public Object find(int shift, int hash, Object key, Object notFound){
		int idx = findIndex(key);
		if(idx < 0)
			return notFound;
		if(Util.equiv(key, array[idx]))
			return array[idx+1];
		return notFound;
	}

	public ISeq nodeSeq(){
		return NodeSeq.create(array, array.length);
	}

    public Iterator iterator(IFn f){
        return new NodeIter(array, f);
    }

    public Object kvreduce(IFn f, Object init){
         return NodeSeq.kvreduce(array,f,init);
    }

	public Object fold(IFn combinef, IFn reducef, IFn fjtask, IFn fjfork, IFn fjjoin){
		return NodeSeq.kvreduce(array, reducef, combinef.invoke());
	}

	public int findIndex(Object key){
		for(int i = 0; i < 2*count; i+=2)
			{
			if(Util.equiv(key, array[i]))
				return i;
			}
		return -1;
	}

	private HashCollisionNode ensureEditable(AtomicReference<Thread> edit){
		if(this.edit == edit)
			return this;
		Object[] newArray = new Object[2*(count+1)]; // make room for next assoc
		System.arraycopy(array, 0, newArray, 0, 2*count);
		return new HashCollisionNode(edit, hash, count, newArray);
	}

	private HashCollisionNode ensureEditable(AtomicReference<Thread> edit, int count, Object[] array){
		if(this.edit == edit) {
			this.array = array;
			this.count = count;
			return this;
		}
		return new HashCollisionNode(edit, hash, count, array);
	}

	private HashCollisionNode editAndSet(AtomicReference<Thread> edit, int i, Object a) {
		HashCollisionNode editable = ensureEditable(edit);
		editable.array[i] = a;
		return editable;
	}

	private HashCollisionNode editAndSet(AtomicReference<Thread> edit, int i, Object a, int j, Object b) {
		HashCollisionNode editable = ensureEditable(edit);
		editable.array[i] = a;
		editable.array[j] = b;
		return editable;
	}


	public INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box addedLeaf){
		if(hash == this.hash) {
			int idx = findIndex(key);
			if(idx != -1) {
				if(array[idx + 1] == val)
					return this;
				return editAndSet(edit, idx+1, val); 
			}
			if (array.length > 2*count) {
				addedLeaf.val = addedLeaf;
				HashCollisionNode editable = editAndSet(edit, 2*count, key, 2*count+1, val);
				editable.count++;
				return editable;
			}
			Object[] newArray = new Object[array.length + 2];
			System.arraycopy(array, 0, newArray, 0, array.length);
			newArray[array.length] = key;
			newArray[array.length + 1] = val;
			addedLeaf.val = addedLeaf;
			return ensureEditable(edit, count + 1, newArray);
		}
		// nest it in a bitmap node
		return new BitmapIndexedNode(edit, 0, bitpos(this.hash, shift) , new Object[] {this})
			.assoc(edit, shift, hash, key, val, addedLeaf);
	}	

	public INode without(AtomicReference<Thread> edit, int shift, int hash, Object key, Box removedLeaf){
		int idx = findIndex(key);
		if(idx == -1)
			return this;
		removedLeaf.val = removedLeaf;
		if(count == 1)
			return null;
		HashCollisionNode editable = ensureEditable(edit);
		editable.array[idx] = editable.array[2*count-2];
		editable.array[idx+1] = editable.array[2*count-1];
		editable.array[2*count-2] = editable.array[2*count-1] = null;
		editable.count--;
		return editable;
	}
	public byte sizePredicate() {
		return 2;
	}

	public Object getKey(int idx) {
		return array[2*idx];
	}

	public Object getValue(int idx) {
		return array[2*idx+1];
	}

}

/*
public static void main(String[] args){
	try
		{
		ArrayList words = new ArrayList();
		Scanner s = new Scanner(new File(args[0]));
		s.useDelimiter(Pattern.compile("\\W"));
		while(s.hasNext())
			{
			String word = s.next();
			words.add(word);
			}
		System.out.println("words: " + words.size());
		IPersistentMap map = PersistentHashMap2.EMPTY;
		//IPersistentMap map = new PersistentTreeMap();
		//Map ht = new Hashtable();
		Map ht = new HashMap();
		Random rand;

		System.out.println("Building map");
		long startTime = System.nanoTime();
		for(Object word5 : words)
			{
			map = map.assoc(word5, word5);
			}
		rand = new Random(42);
		IPersistentMap snapshotMap = map;
		for(int i = 0; i < words.size() / 200; i++)
			{
			map = map.without(words.get(rand.nextInt(words.size() / 2)));
			}
		long estimatedTime = System.nanoTime() - startTime;
		System.out.println("count = " + map.count() + ", time: " + estimatedTime / 1000000);

		System.out.println("Building ht");
		startTime = System.nanoTime();
		for(Object word1 : words)
			{
			ht.put(word1, word1);
			}
		rand = new Random(42);
		for(int i = 0; i < words.size() / 200; i++)
			{
			ht.remove(words.get(rand.nextInt(words.size() / 2)));
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("count = " + ht.size() + ", time: " + estimatedTime / 1000000);

		System.out.println("map lookup");
		startTime = System.nanoTime();
		int c = 0;
		for(Object word2 : words)
			{
			if(!map.contains(word2))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		System.out.println("ht lookup");
		startTime = System.nanoTime();
		c = 0;
		for(Object word3 : words)
			{
			if(!ht.containsKey(word3))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		System.out.println("snapshotMap lookup");
		startTime = System.nanoTime();
		c = 0;
		for(Object word4 : words)
			{
			if(!snapshotMap.contains(word4))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		}
	catch(FileNotFoundException e)
		{
		e.printStackTrace();
		}

}
*/

//private static INode[] cloneAndSet(INode[] array, int i, INode a) {
//	INode[] clone = array.clone();
//	clone[i] = a;
//	return clone;
//}

private static Object[] cloneAndSet(Object[] array, int i, Object a) {
	Object[] clone = array.clone();
	clone[i] = a;
	return clone;
}

private static Object[] cloneAndSet(Object[] array, int i, Object a, int j, Object b) {
	Object[] clone = array.clone();
	clone[i] = a;
	clone[j] = b;
	return clone;
}

private static Object[] removePair(Object[] array, int i) {
	Object[] newArray = new Object[array.length - 2];
	System.arraycopy(array, 0, newArray, 0, 2*i);
	System.arraycopy(array, 2*(i+1), newArray, 2*i, newArray.length - 2*i);
	return newArray;
}

private static INode createNode(int shift, Object key1, Object val1, int key2hash, Object key2, Object val2) {
	int key1hash = hash(key1);
	if(key1hash == key2hash)
		return new HashCollisionNode(null, key1hash, 2, new Object[] {key1, val1, key2, val2});
	Box addedLeaf = new Box(null);
	AtomicReference<Thread> edit = new AtomicReference<Thread>();
	return BitmapIndexedNode.EMPTY
		.assoc(edit, shift, key1hash, key1, val1, addedLeaf)
		.assoc(edit, shift, key2hash, key2, val2, addedLeaf);
}

private static INode createNode(AtomicReference<Thread> edit, int shift, Object key1, Object val1, int key2hash, Object key2, Object val2) {
	int key1hash = hash(key1);
	if(key1hash == key2hash)
		return new HashCollisionNode(null, key1hash, 2, new Object[] {key1, val1, key2, val2});
	Box addedLeaf = new Box(null);
	return BitmapIndexedNode.EMPTY
		.assoc(edit, shift, key1hash, key1, val1, addedLeaf)
		.assoc(edit, shift, key2hash, key2, val2, addedLeaf);
}

private static int bitpos(int hash, int shift){
	return 1 << mask(hash, shift);
}

static final class NodeIter implements Iterator {
    private static final Object NULL = new Object();
    final Object[] array;
    final IFn f;
    private int i = 0;
    private Object nextEntry = NULL;
    private Iterator nextIter;

    NodeIter(Object[] array, IFn f){
        this.array = array;
        this.f = f;
    }

    private boolean advance(){
        while (i<array.length)
        {
            Object key = array[i];
            Object nodeOrVal = array[i+1];
            i += 2;
            if (key != null)
            {
                nextEntry = f.invoke(key, nodeOrVal);
                return true;
            }
            else if(nodeOrVal != null)
            {
                Iterator iter = ((INode) nodeOrVal).iterator(f);
                if(iter != null && iter.hasNext())
                {
                    nextIter = iter;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasNext(){
        if (nextEntry != NULL || nextIter != null)
            return true;
        return advance();
    }

    public Object next(){
        Object ret = nextEntry;
        if(ret != NULL)
        {
            nextEntry = NULL;
            return ret;
        }
        else if(nextIter != null)
        {
            ret = nextIter.next();
            if(! nextIter.hasNext())
                nextIter = null;
            return ret;
        }
        else if(advance())
            return next();
        throw new NoSuchElementException();
    }

    public void remove(){
        throw new UnsupportedOperationException();
    }
}

static final class NodeSeq extends ASeq {
	final Object[] array;
	final int threshold;  //
	final int i;
	final ISeq s;
	
	NodeSeq(Object[] array, int threshold, int i) {
		this(null, array, threshold, i, null);
	}

	static ISeq create(Object[] array, int threshold) {
		return create(array, threshold, 0, null);
	}

	private static ISeq create(Object[] array, int threshold, int i, ISeq s) {
		if(i < threshold || s != null)
			return new NodeSeq(null, array, threshold, i, s);
		if (i < array.length) {
			INode node = (INode) array[i];
			ISeq nodeSeq = node.nodeSeq();
			return new NodeSeq(null, array, threshold, i + 1, nodeSeq);
		}
		return null;
	}

    static public Object kvreduce(Object[] array, IFn f, Object init){
		return null;
    }

	NodeSeq(IPersistentMap meta, Object[] array, int threshold, int i, ISeq s) {
		super(meta);
		this.array = array;
		this.threshold = threshold;
		this.i = i;
		this.s = s;
	}

	public Obj withMeta(IPersistentMap meta) {
		return new NodeSeq(meta, array, threshold, i, s);
	}

	public Object first() {
		if(s != null)
			return s.first();
		return MapEntry.create(array[i], array[i+1]);
	}

	public ISeq next() {
		if(s != null)
			return create(array, threshold, i, s.next());
		return create(array, threshold, i + 2, null);
	}
}

}
