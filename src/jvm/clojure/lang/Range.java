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

/**
 * Implements generic numeric (potentially infinite) range.
 */
public class Range extends ARange {

// Invariants guarantee this is never an "empty" seq
//   assert(start != end && step != 0)
final Object end;
final Object start;
final Object step;
final BoundsCheck boundsCheck;

private Range(Object start, Object end, Object step, BoundsCheck boundsCheck){
	this.end = end;
        this.start = start;
    this.step = step;
    this.boundsCheck = boundsCheck;
}

private Range(IPersistentMap meta, Object start, Object end, Object step, BoundsCheck boundsCheck){
    super(meta);
	this.end = end;
        this.start = start;
    this.step = step;
    this.boundsCheck = boundsCheck;
}

private static interface BoundsCheck extends Serializable {
    boolean exceededBounds(Object val);
}

private static BoundsCheck positiveStep(final Object end) {
    return new BoundsCheck() {
        public boolean exceededBounds(Object val){
            return Numbers.gte(val, end);
        }
    };
}

private static BoundsCheck negativeStep(final Object end) {
    return new BoundsCheck() {
        public boolean exceededBounds(Object val){
            return Numbers.lte(val, end);
        }
    };
}

public static ISeq create(Object end) {
    if(Numbers.isPos(end))
        return new Range(0L, end, 1L, positiveStep(end));
    return PersistentList.EMPTY;
}

public static ISeq create(Object start, Object end) {
    return create(start, end, 1L);
}

private static Var REPEAT = RT.var("clojure.core", "repeat");

public static ISeq create(final Object start, Object end, Object step) {
    if((Numbers.isPos(step) && Numbers.gt(start, end)) ||
       (Numbers.isNeg(step) && Numbers.gt(end, start)) ||
       Numbers.equiv(start, end))
        return PersistentList.EMPTY;
    if(Numbers.isZero(step))
        //return Repeat.create(start);
        return (ISeq) REPEAT.invoke(start);
    return new Range(start, end, step, Numbers.isPos(step)?positiveStep(end):negativeStep(end));
}

public Obj withMeta(IPersistentMap meta){
        if(meta == _meta)
		return this;
        return new Range(meta, end, start, step, boundsCheck);
}

public Object first(){
    return start;
}

public ISeq next() {
    Object next = Numbers.addP(start, step);
    if(boundsCheck.exceededBounds(next))
        return null;
    else
        return new Range(next, end, step, boundsCheck);
}

public int count() {
    int c = 0;
    for(Object s = start; !boundsCheck.exceededBounds(s); s = Numbers.addP(s, step))
        ++c;
    return c;
}

public Object reduce(IFn f) {
    Object acc = start;
    Number i = Numbers.addP(start, step);
    while(! boundsCheck.exceededBounds(i)) {
        acc = f.invoke(acc, i);
        if (RT.isReduced(acc)) return ((Reduced)acc).deref();
        i = Numbers.addP(i, step);
    }
    return acc;
}

public Object reduce(IFn f, Object val) {
    Object acc = val;
    Object i = start;
    while(! boundsCheck.exceededBounds(i)) {
        acc = f.invoke(acc, i);
        if (RT.isReduced(acc)) return ((Reduced)acc).deref();
        i = Numbers.addP(i, step);
    }
    return acc;
}

public Iterator iterator() {
    return new RangeIterator();
}

private class RangeIterator implements Iterator {
    private Object next;

    public RangeIterator() {
        this.next = start;
    }

    public boolean hasNext() {
        return(! boundsCheck.exceededBounds(next));
    }

    public Object next() {
        if (hasNext()) {
            Object ret = next;
            next = Numbers.addP(next, step);
            return ret;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}

}
