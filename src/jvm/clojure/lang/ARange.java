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

abstract class ARange extends Obj implements ISeq, Sequential, Serializable, IHashEq, Counted, List, IReduce {

transient int _hash = -1;
transient int _hasheq = -1;

protected ARange() {
}

protected ARange(IPersistentMap meta) {
    super(meta);
}

public ISeq seq() {
    return this;
}

public ISeq more() {
    ISeq next = next();
    if(next == null)
        return PersistentList.EMPTY;
    return next;
}

public ISeq cons(Object o) {
    return new Cons(o, this);
}

public IPersistentCollection empty() {
    return PersistentList.EMPTY;
}

public boolean equiv(Object obj) {
    if(!(obj instanceof Sequential || obj instanceof List))
        return false;

    ISeq ms = RT.seq(obj);
    for (Object o : this)
    {
        if(ms == null || !Util.equiv(o, ms.first()))
            return false;

        ms = ms.next();
    }
    return ms == null;
}

public int hasheq() {
    if (_hasheq == -1)
        _hasheq = Murmur3.hashOrdered(this);
    return _hasheq;
}

//////////// List stuff /////////////////

public int size() {
    return count();
}

public boolean isEmpty() {
    return false;
}

public boolean contains(Object o) {
    Iterator itr = iterator();
    while (itr.hasNext()) {
        Object v = itr.next();
        if (Util.equiv(v, o)) return true;
    }
    return false;
}

public int hashCode() {
    if(_hash == -1)
    {
        int hash = 1;
        for(Object o : this)
        {
            hash = 31 * hash + (o == null ? 0 : o.hashCode());
        }
        this._hash = hash;
    }
    return _hash;
}

public boolean equals(Object obj) {
    if(!(obj instanceof Sequential || obj instanceof List))
        return false;

    ISeq ms = RT.seq(obj);
    for (Object o : this)
    {
        if(ms == null || !Util.equals(o, ms.first()))
            return false;

        ms = ms.next();
    }
    return ms == null;
}

public String toString() {
    return RT.printString(this);
}

private List reify() {
    return Collections.unmodifiableList(this);
}

public Object[] toArray() {
    return RT.seqToArray(this);
}

public boolean add(Object o) {
    throw new UnsupportedOperationException();
}

public boolean remove(Object o) {
    throw new UnsupportedOperationException();
}

public boolean addAll(Collection c) {
    throw new UnsupportedOperationException();
}

public boolean addAll(int index, Collection c) {
    throw new UnsupportedOperationException();
}

public void clear() {
    throw new UnsupportedOperationException();
}

public Object get(int index) {
    return RT.nth(this, index);
}

public Object set(int index, Object element) {
    throw new UnsupportedOperationException();
}

public void add(int index, Object element) {
    throw new UnsupportedOperationException();
}

public Object remove(int index) {
    throw new UnsupportedOperationException();
}

public int indexOf(Object o) {
    return reify().indexOf(o);
}

public int lastIndexOf(Object o) {
    return reify().lastIndexOf(o);
}

public ListIterator listIterator() {
    return reify().listIterator();
}

public ListIterator listIterator(int index) {
    return reify().listIterator(index);
}

public List subList(int fromIndex, int toIndex) {
    return reify().subList(fromIndex, toIndex);
}

public boolean retainAll(Collection c) {
    throw new UnsupportedOperationException();
}

public boolean removeAll(Collection c) {
    throw new UnsupportedOperationException();
}

public boolean containsAll(Collection c) {
    for (Object o : c)
        if (!contains(o)) return false;
    return true;
}

public Object[] toArray(Object[] a) {
    return RT.seqToPassedArray(this, a);
}

}
