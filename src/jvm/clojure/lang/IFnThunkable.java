package clojure.lang;

@FunctionalInterface
public interface IFnThunkable {
    IFn getFnThunk(Object kw);
}
