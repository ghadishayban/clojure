/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Jan 31, 2009 */

package clojure.lang;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;

public final class LazySeq extends Obj implements ISeq, Sequential, List, IPending, IHashEq{

	private static final VarHandle STATE;
	private static final byte UNREALIZED = 0;
	private static final byte SVAL = 1;
	private static final byte SEQ = 2;
	private static final byte ERROR = 3;
	private static final byte EXCLUSIVE = 4;
	private static final byte CONTENDED = 5;

	static {
		try {
			MethodHandles.Lookup l = MethodHandles.lookup();
			STATE = l.findVarHandle(LazySeq.class, "state", byte.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private volatile Object obj;
	private volatile byte state;

public LazySeq(IFn fn){
	this.obj = fn;
	this.state = UNREALIZED;
}

private LazySeq(IPersistentMap meta, ISeq s){
	super(meta);
	this.obj = s;
	this.state = SEQ;
}

public Obj withMeta(IPersistentMap meta){
	if(meta() == meta)
		return this;
	return new LazySeq(meta, seq());
}

	static private ISeq asSeq(Object sv) {
		if (sv != null) {
			Object ls = sv;
			while (ls instanceof LazySeq)
				ls = ((LazySeq) ls).sval();
			return RT.seq(ls);
		}
		return null;
	}

	final private Object sval() {
		for (; ; ) {
			byte s = state;
			Object o = obj;
			switch (s) {
				case UNREALIZED:
					if (casState(UNREALIZED, EXCLUSIVE))
						return thunkToSval(o);
					break;
				case SVAL:
					return o;
				case SEQ:
					return o;
				case ERROR:
					Util.sneakyThrow((Throwable) o);
				case EXCLUSIVE:
					casState(EXCLUSIVE, CONTENDED);
					break;
				case CONTENDED:
					await();
					break;
			}
		}
	}

	private boolean casState(byte witness, byte value) {
		return STATE.compareAndSet(this, witness, value);
	}

	private void signalState(byte newState) {
		if (casState(EXCLUSIVE, newState)) {
			return;
		}
		if (casState(CONTENDED, newState)) {
			synchronized (this) {
				notifyAll();
			}
			return;
		}
		throw new IllegalStateException();
	}

	private void await() {
		synchronized (this) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	final public ISeq seq() {
		for (; ; ) {
			byte s = state;
			Object o = obj;
			switch (s) {
				case UNREALIZED:
					if (casState(UNREALIZED, EXCLUSIVE))
						return thunkToSeq(o);
					break;
				case SVAL:
					if (casState(SVAL, EXCLUSIVE))
						return svalToSeq(o);
					break;
				case SEQ:
					return (ISeq) o;
				case ERROR:
					Util.sneakyThrow((Throwable) o);
				case EXCLUSIVE:
					casState(EXCLUSIVE, CONTENDED);
					break;
				case CONTENDED:
					await();
					break;
			}
		}
	}

	private Object thunkToSval(Object thunk) {
		try {
			Object ret = ((IFn) thunk).invoke();
			obj = ret;
			signalState(SVAL);
			return ret;
		} catch (Throwable e) {
			obj = e;
			signalState(ERROR);
			throw e;
		}
	}

	private ISeq svalToSeq(Object sval) {
		try {
			ISeq ret = asSeq(sval);
			obj = ret;
			signalState(SEQ);
			return ret;
		} catch (Throwable e) {
			obj = e;
			signalState(ERROR);
			throw e;
		}
	}

	private ISeq thunkToSeq(Object thunk) {
		try {
			ISeq ret = asSeq(((IFn) thunk).invoke());
			obj = ret;
			signalState(SEQ);
			return ret;
		} catch (Throwable e) {
			obj = e;
			signalState(ERROR);
			throw e;
		}
	}

public int count(){
	int c = 0;
	for(ISeq s = seq(); s != null; s = s.next())
		++c;                                                                                
	return c;
}

public Object first(){
	ISeq s = seq();
	if(s == null)
		return null;
	return s.first();
}

public ISeq next(){
	ISeq s = seq();
	if(s == null)
		return null;
	return s.next();	
}

public ISeq more(){
	ISeq s = seq();
	if(s == null)
		return PersistentList.EMPTY;
	return s.more();
}

public ISeq cons(Object o){
	return RT.cons(o, seq());
}

public IPersistentCollection empty(){
	return PersistentList.EMPTY;
}

public boolean equiv(Object o){
	ISeq s = seq();
	if(s != null)
		return s.equiv(o);
	else
		return (o instanceof Sequential || o instanceof List) && RT.seq(o) == null;
}

public int hashCode(){
	ISeq s = seq();
	if(s == null)
		return 1;
	return Util.hash(s);
}

public int hasheq(){
	return Murmur3.hashOrdered(this);
}

public boolean equals(Object o){
	ISeq s = seq();
	if(s != null)
		return s.equals(o);
	else
		return (o instanceof Sequential || o instanceof List) && RT.seq(o) == null;
}


// java.util.Collection implementation

public Object[] toArray(){
	return RT.seqToArray(seq());
}

public boolean add(Object o){
	throw new UnsupportedOperationException();
}

public boolean remove(Object o){
	throw new UnsupportedOperationException();
}

public boolean addAll(Collection c){
	throw new UnsupportedOperationException();
}

public void clear(){
	throw new UnsupportedOperationException();
}

public boolean retainAll(Collection c){
	throw new UnsupportedOperationException();
}

public boolean removeAll(Collection c){
	throw new UnsupportedOperationException();
}

public boolean containsAll(Collection c){
	for(Object o : c)
		{
		if(!contains(o))
			return false;
		}
	return true;
}

public Object[] toArray(Object[] a){
    return RT.seqToPassedArray(seq(), a);
}

public int size(){
	return count();
}

public boolean isEmpty(){
	return seq() == null;
}

public boolean contains(Object o){
	for(ISeq s = seq(); s != null; s = s.next())
		{
		if(Util.equiv(s.first(), o))
			return true;
		}
	return false;
}

public Iterator iterator(){
	return new SeqIterator(this);
}

//////////// List stuff /////////////////
private List reify(){
	return new ArrayList(this);
}

public List subList(int fromIndex, int toIndex){
	return reify().subList(fromIndex, toIndex);
}

public Object set(int index, Object element){
	throw new UnsupportedOperationException();
}

public Object remove(int index){
	throw new UnsupportedOperationException();
}

public int indexOf(Object o){
	ISeq s = seq();
	for(int i = 0; s != null; s = s.next(), i++)
		{
		if(Util.equiv(s.first(), o))
			return i;
		}
	return -1;
}

public int lastIndexOf(Object o){
	return reify().lastIndexOf(o);
}

public ListIterator listIterator(){
	return reify().listIterator();
}

public ListIterator listIterator(int index){
	return reify().listIterator(index);
}

public Object get(int index){
	return RT.nth(this, index);
}

public void add(int index, Object element){
	throw new UnsupportedOperationException();
}

public boolean addAll(int index, Collection c){
	throw new UnsupportedOperationException();
}


public boolean isRealized(){
	return state != SVAL && state != SEQ;
}
}
