package org.example.util;


/*
 * A typed mutable container for sharing a value between steps in a single transaction.
 * Java requires lambda-captured variables to be effectively final, which means that lambda cannot
 * assign to an outer local variable directly. Ref is  keeping the
 * reference to the holder final while allowing its contents to change.
 */
public final class Ref<T> {
    private T value;

    private Ref() {}

    public static <T> Ref<T> empty() {
        return new Ref<>();
    }

    public T get() {
        if (value == null) {
            throw new IllegalStateException(
                    "Ref is not populated yet. Ensure selectMode runs before access."
            );
        }

        return value;
    }

    void set(
            T value
    ) {
        this.value = value;
    }
}