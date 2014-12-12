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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements the special common case of a finite range based on long start, end, and step.
 */
public class LongRange extends ARange implements IChunkedSeq {

private static final int CHUNK_SIZE = 32;

// Invariants guarantee this is never an "empty" seq
//   assert(start != end && step != 0)
final long end;
final long step;
final BoundsCheck boundsCheck;
final LongArrayChunk chunk;

private static interface BoundsCheck extends Serializable {
    boolean exceededBounds(long val);
}

private static BoundsCheck positiveStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val >= end);
        }
    };
}

private static BoundsCheck negativeStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val <= end);
        }
    };
}

private LongRange(long end, long step, BoundsCheck boundsCheck, LongArrayChunk chunk){
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
    this.chunk = chunk;
}

private LongRange(IPersistentMap meta, long end, long step, BoundsCheck boundsCheck, LongArrayChunk chunk){
    super(meta);
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
    this.chunk = chunk;
}

private static LongArrayChunk makeChunk(long start, long step, BoundsCheck boundsCheck) {
    long[] arr = new long[CHUNK_SIZE];
    int i = 0;
    long val = start;
    do {
        arr[i] = val;
        i++;
        val += step;
    } while(! boundsCheck.exceededBounds(val) && i < CHUNK_SIZE);
    return new LongArrayChunk(arr, 0, i);
}

public static ISeq create(long end) {
    if(end > 0) {
        BoundsCheck boundsCheck = positiveStep(end);
        return new LongRange(end, 1L, boundsCheck, makeChunk(0L, 1L, boundsCheck));
    }
    return PersistentList.EMPTY;
}

public static ISeq create(long start, long end) {
    if(start >= end)
        return PersistentList.EMPTY;
    else {
        BoundsCheck boundsCheck = positiveStep(end);
        return new LongRange(end, 1L, boundsCheck, makeChunk(start, 1L, boundsCheck));
    }
}

private static Var REPEAT = RT.var("clojure.core", "repeat");

public static ISeq create(final long start, long end, long step) {
    if(step > 0) {
        if(end <= start) return PersistentList.EMPTY;
        BoundsCheck boundsCheck = positiveStep(end);
        return new LongRange(end, step, boundsCheck, makeChunk(start, step, boundsCheck));
    } else if(step < 0) {
        if(end >= start) return PersistentList.EMPTY;
        BoundsCheck boundsCheck = negativeStep(end);
        return new LongRange(end, step, boundsCheck, makeChunk(start, step, boundsCheck));
    } else {
        if(end == start) return PersistentList.EMPTY;
        //return Repeat.create(start);    // alternate impl when Repeat exists
        return (ISeq) REPEAT.invoke(start);
    }
}

public Obj withMeta(IPersistentMap meta){
    if(meta == _meta)
        return this;
    return new LongRange(meta, end, step, boundsCheck, chunk);
}

public Object first() {
    return chunk.first();
}

public ISeq next() {
    if(chunk.count() > 1)
        return new LongRange(end, step, boundsCheck, (LongArrayChunk)chunk.dropFirst());
    else {
        long next = chunk.first() + step;
        if(boundsCheck.exceededBounds(next))
            return null;
        else
            return new LongRange(end, step, boundsCheck, makeChunk(next, step, boundsCheck));
    }
}

public int count() {
    double c = (end - chunk.first()) / step;
    int ic = (int) c;
    if(c < ic)
        return ic + 1;
    else
        return ic;
}

public Object reduce(IFn f) {
    long start = chunk.first();
    Object acc = start;
    long i = start + step;
    while(! boundsCheck.exceededBounds(i)) {
        acc = f.invoke(acc, i);
        if (acc instanceof Reduced) return ((Reduced)acc).deref();
        i = i + step;
    }
    return acc;
}

public Object reduce(IFn f, Object val) {
    Object acc = chunk.reduce(f, val);
    if(RT.isReduced(acc))
        return ((Reduced)acc).deref();

    long i = chunk.last() + step;
    while(! boundsCheck.exceededBounds(i)) {
        acc = f.invoke(acc, i);
        if (RT.isReduced(acc)) return ((Reduced)acc).deref();
        i = i + step;
    }
    return acc;
}

public Iterator iterator() {
    return new LongRangeIterator();
}

class LongRangeIterator implements Iterator {
    private long next;

    public LongRangeIterator() {
        this.next = chunk.first();
    }

    public boolean hasNext() {
        return(! boundsCheck.exceededBounds(next));
    }

    public Object next() {
        if (hasNext()) {
            long ret = next;
            next = next + step;
            return ret;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}

public ISeq seq() {
    return this;
}

//////////// Chunked Seq Stuff //////////

public IChunk chunkedFirst() {
    return chunk;
}

public ISeq chunkedNext() {
    long nextStart = chunk.last() + step;
    if(boundsCheck.exceededBounds(nextStart))
        return null;
    else
        return new LongRange(end, step, boundsCheck, makeChunk(nextStart, step, boundsCheck));
}

public ISeq chunkedMore() {
    ISeq next = chunkedNext();
    if(next == null)
        return PersistentList.EMPTY;
    return next;
}

// same as ArrayChunk, but with long[]
private static class LongArrayChunk implements IChunk, Serializable {
    final long[] array;
    final int off;
    final int end;

    public LongArrayChunk(long[] array){
        this(array, 0, array.length);
    }

    public LongArrayChunk(long[] array, int off){
        this(array, off, array.length);
    }

    public LongArrayChunk(long[] array, int off, int end){
        this.array = array;
        this.off = off;
        this.end = end;
    }

    public long first() {
        return array[off];
    }

    public long last() {
        return array[end - 1];
    }

    public Object nth(int i){
        return array[off + i];
    }

    public Object nth(int i, Object notFound){
        if(i >= 0 && i < count())
            return nth(i);
        return notFound;
    }

    public int count(){
        return end - off;
    }

    public IChunk dropFirst(){
        if(off==end)
            throw new IllegalStateException("dropFirst of empty chunk");
        return new LongArrayChunk(array, off + 1, end);
    }

    public Object reduce(IFn f, Object start) {
        Object ret = f.invoke(start, array[off]);
        if(RT.isReduced(ret))
            return ret;
        for(int x = off + 1; x < end; x++) {
            ret = f.invoke(ret, array[x]);
            if(RT.isReduced(ret))
                return ret;
        }
        return ret;
    }
}

}