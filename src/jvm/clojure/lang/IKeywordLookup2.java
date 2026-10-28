package clojure.lang;

@FunctionalInterface
public interface IKeywordLookup2 {
    IFn getLookupFn(Keyword kw);
}
