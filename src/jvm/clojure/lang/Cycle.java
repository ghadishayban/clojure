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

/* Alex Miller, Dec 5, 2014 */

public class Cycle extends ASeq implements IReduce {

private final ISeq all;      // never null
private final ISeq current;  // never null

private Cycle(ISeq all, ISeq current){
    this.all = all;
    this.current = current;
}

private Cycle(IPersistentMap meta, ISeq all, ISeq current){
    super(meta);
    this.all = all;
    this.current = current;
}

public static ISeq create(ISeq vals){
    if(vals == null)
        return PersistentList.EMPTY;
    return new Cycle(vals, vals);
}

public Object first(){
    return current.first();
}

public ISeq next(){
    ISeq next = current.next();
    if(next != null)
        return new Cycle(all, next);
    return new Cycle(all, all);
}

public Cycle withMeta(IPersistentMap meta){
    return new Cycle(meta, all, current);
}

public Object reduce(IFn f){
    Object ret = first();
    for(ISeq s = next(); s != null; s = s.next()) {
        ret = f.invoke(ret, s.first());
        if (RT.isReduced(ret)) return ((IDeref)ret).deref();;
    }
    return ret;
}

public Object reduce(IFn f, Object start){
    Object ret = start;
    ISeq s = current;
    while(true){
        ret = f.invoke(ret, s.first());
        if(ret instanceof Reduced)
            return ((IDeref)ret).deref();
        s = current.next();
        if(s == null)
            s = all;
    }
}
}
