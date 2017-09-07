package labut.md311.lookbook;

//helper generic class
public class Pair<T> {
    private final T first_val;
    private final T second_val;

    public Pair(T first_val, T second_val) {
        this.first_val = first_val;
        this.second_val = second_val;
    }

    public T first_value() {
        return this.first_val;
    }

    public T second_value() {
        return this.second_val;
    }
}
