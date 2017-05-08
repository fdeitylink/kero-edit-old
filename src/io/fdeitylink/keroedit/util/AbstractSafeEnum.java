/*
 * TODO:
 * Custom Map implementation similar to EnumMap?
 * Custom Set implementation similar to EnumSet?
 * Require fields inside constant to be transient (normal enums do not maintain state)
 *  - Or create specific serialization method that cannot be changed in subclasses
 * Figure out how serialization should work
 */

package io.fdeitylink.keroedit.util;

import java.util.Arrays;

import java.util.Comparator;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;

import java.io.Serializable;

public abstract class AbstractSafeEnum <E extends AbstractSafeEnum<E> & AbstractSafeEnum.Extensions <E>>
        implements Comparable <AbstractSafeEnum<E>>, Serializable {
    private static final long serialVersionUID = 1L;

    /*
     * TODO:
     * Is the type parameter relationship above retained in the wildcards here?
     * Make non-transient?
     *  - If ObjectInputStream.readObject() does not end up calling AbstractSafeEnum constructor,
     *    make this non-transient and provide custom read/writeObject() methods
     */
    private static transient final
    Map <Class <? extends AbstractSafeEnum<?>>, List <? extends AbstractSafeEnum<?>>> subclassMap = new HashMap <>();

    private static transient final Comparator <Field> fieldComparator = (fieldOne, fieldTwo) -> {
        NullArgumentException.requireNonNull(fieldOne, "fieldComparator.compare()", "o1");
        NullArgumentException.requireNonNull(fieldTwo, "fieldComparator.compare()", "o2");

        final OrderedMember fieldOneOrder = fieldOne.getAnnotation(OrderedMember.class);
        final OrderedMember fieldTwoOrder = fieldTwo.getAnnotation(OrderedMember.class);

        if (null == fieldOneOrder) {
            throw new IllegalStateException("Field " + fieldOne.getName() + " is not annotated with OrderedMember annotation");
        }
        if (null == fieldTwoOrder) {
            throw new IllegalStateException("Field " + fieldTwo.getName() + " is not annotated with OrderedMember annotation");
        }

        final int orderOne = fieldOneOrder.value();
        final int orderTwo = fieldTwoOrder.value();

        if (0 > orderOne) {
            throw new IllegalStateException("value() for OrderedMember annotation for field " + fieldOne.getName() +
                                            " is negative (value(): " + orderOne + ')');
        }

        if (0 > orderTwo) {
            throw new IllegalStateException("value() for OrderedMember annotation for field " + fieldTwo.getName() +
                                            " is negative (value(): " + orderTwo + ')');
        }

        if (orderOne == orderTwo) {
            throw new IllegalStateException("value() for OrderedMember annotation for fields " +
                                            fieldOne.getName() + " and " + fieldTwo.getName() + " are equivalent " +
                                            "(value(): " + fieldOneOrder.value() + ')');
        }

        return orderOne - orderTwo;
    };

    private final String name;
    private final int ordinal;

    @SuppressWarnings("unchecked")
    protected AbstractSafeEnum(final String name, final int ordinal) {
        NullArgumentException.requireNonNull(name, "AbstractSafeEnum", "name");
        if (ordinal < 0) {
            throw new IllegalArgumentException("Attempt to construct new AbstractSafeEnum with negative ordinal " +
                                               "(ordinal: " + ordinal + ')');
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Attempt to construct new AbstractSafeEnum with empty name");
        }

        /*
         * TODO:
         * Disallow abstract classes?
         *  - Though I'd like to have this - allow abstract methods if all constants are initialized
         *    as anonymous classes that make the methods concrete
         */
        final Class <E> directSubclass = getDeclaringClass();

        /*
         * Note to self - do not put this if/elseif block into the
         * if (!subclassMap.contains(directSubclass)) block
         * Right here we're verifying that the Class of this object meets the
         * requirements, and as it is instance-dependent, it must be checked
         * every time this constructor is called, as the constructor is called
         * once for every new object. The class verification code in the if-block
         * need run only once as the constructors and class modifiers won't change
         * during program execution.
         */
        final Class <?> exactClass = getClass();
        if (exactClass.isAnonymousClass()) {
            if (!directSubclass.equals(exactClass.getEnclosingClass())) {
                //If exactClass is anonymous, it must be declared inside directSubclass, otherwise throw except
                throw new IllegalStateException("Anonymous subclass " + exactClass + " of AbstractSafeEnum subclass " +
                                                directSubclass + " is not declared inside of " + directSubclass);
            }
        }
        else if (!directSubclass.equals(exactClass)/* && directSubclass.isAssignableFrom(exactClass)*/) {
            //If exactClass is not anonymous, it must explicitly be directSubclass, not a subclass, otherwise throw except
            throw new IllegalStateException("Non-anonymous class " + exactClass + " is subclass of AbstractSafeEnum subclass " +
                                            directSubclass + " when the two classes must be equivalent");
        }

        final List <AbstractSafeEnum<E>> constants;
        if (!subclassMap.containsKey(directSubclass)) {
            /* ******************************************* Verify Subclass ****************************************** */
            final Constructor <?>[] constructors = directSubclass.getDeclaredConstructors();
            for (final Constructor <?> c : constructors) {
                //Synthetic constructors called upon anonymous class creation won't be private like the normal constructors
                //http://stackoverflow.com/a/14267503
                //TODO: Prevent calling the constructors through reflection or outer classes
                if (!c.isSynthetic() && !Modifier.isPrivate(c.getModifiers())) {
                    throw new IllegalStateException("Constructor " + c + " for AbstractSafeEnum subclass " + directSubclass +
                                                    " must be private");
                }
            }

            final int subclassModifiers = directSubclass.getModifiers();
            if (directSubclass.isMemberClass() && !Modifier.isStatic(subclassModifiers)) {
                throw new IllegalStateException("AbstractSafeEnum subclass " + directSubclass + " is not static yet is a member class");
            }

            if (Modifier.isFinal(subclassModifiers)) {
                throw new IllegalStateException("AbstractSafeEnum subclass " + directSubclass + " is final");
            }

            /* ****************************************** Verify Arguments ****************************************** */
            if (0 != ordinal) {
                throw new IllegalArgumentException("Ordinal for first object of AbstractSafeEnum subclass " + directSubclass +
                                                   " must be 0 (ordinal: " + ordinal + ')');
            }

            final List <Field> fields = Arrays.asList(directSubclass.getDeclaredFields());
            fields.removeIf(field -> {
                final int fieldModifiers = field.getModifiers();
                return !(directSubclass.isAssignableFrom(field.getType()) &&
                         Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers));
            });

            if (0 == fields.size()) {
                throw new IllegalStateException("AbstractSafeEnum subclass " + directSubclass + " has no fields for constants, " +
                                                "yet is attempting to construct new instance");
            }

            fields.sort(fieldComparator); //also throws except for missing (null) annotations

            final Field field = fields.get(0);
            final OrderedMember orderAnnotation = field.getAnnotation(OrderedMember.class);

            if (orderAnnotation.value() != 0) {
                throw new IllegalArgumentException("First field " + field + " of AbstractSafeEnum subclass " + directSubclass +
                                                   " does not have value() of 0 for its OrderedMember annotation " +
                                                   "(value(): " + orderAnnotation.value() + ')');
            }
            if (!field.getName().equals(name)) {
                throw new IllegalArgumentException("First field " + field + " of AbstractSafeEnum subclass " + directSubclass +
                                                   " does not have same name as given name " +
                                                   "(field.getName(): " + field.getName() + ", name: " + name + ')');
            }

            constants = new ArrayList <>();
            subclassMap.put(directSubclass, constants);
        }
        else {
            constants = (List <AbstractSafeEnum<E>>)subclassMap.get(directSubclass);

            final AbstractSafeEnum<E> prevConstructedConstant = constants.get(constants.size() - 1);
            final int expectedOrdinal = prevConstructedConstant.ordinal + 1;
            if (expectedOrdinal != ordinal) {
                throw new IllegalArgumentException("Expected ordinal value does not match given " +
                                                   "(expected: " + expectedOrdinal + ", ordinal: " + ordinal + ')');
            }

            /*
             * Because fields that are not static and not final are filtered out, there
             * is a compile-time assurance that something like the following won't occur.
             *     static final SafeEnumSubclass X;
             *     static {
             *         new SafeEnumSubclass("X", 0);
             *     }
             * However, the caveat is that if X were assigned to null, the code would compile,
             * and the constructed object would not be assigned to it.
             *
             * TODO: Is there any way to protect against the above (check field value following construction?)
             */

            /*
             * TODO:
             * Suppose a AbstractSafeEnum subclass has two constants defined, X and Y, in that order.
             * If the OrderedMember annotations are mixed up (such that X's has a value() of 1
             * and Y's has a value() of 0), and the names are mixed up (such that X's is "Y"
             * and Y's is "X"), this won't be registered as an issue, so the names could be mixed up.
             */

            final List <Field> fields = Arrays.asList(directSubclass.getDeclaredFields());
            fields.removeIf(field -> {
                final int modifiers = field.getModifiers();
                return !(directSubclass.isAssignableFrom(field.getType()) &&
                         Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
            });

            if (ordinal >= fields.size()) {
                throw new IllegalArgumentException("Given ordinal exceeds maximum index for constants in AbstractSafeEnum subclass " +
                                                   directSubclass +
                                                   " (constants: " + fields.size() + ", ordinal: " + ordinal + ')');
            }

            fields.sort(fieldComparator); //also throws except for missing (null) annotations

            final Field field = fields.get(ordinal);
            final OrderedMember orderAnnotation = field.getAnnotation(OrderedMember.class);

            if (orderAnnotation.value() != ordinal) {
                throw new IllegalArgumentException("Given ordinal does not match value() for corresponding field " +
                                                   field + "'s OrderedMember annotation in AbstractSafeEnum subclass " +
                                                   directSubclass +
                                                   "(value(): " + orderAnnotation.value() + ", ordinal: " + ordinal + ')');
            }
            if (!field.getName().equals(name)) {
                throw new IllegalArgumentException("Given name does not match name of field at given ordinal " +
                                                   "(field.getName(): " + field.getName() + ", ordinal: " + ordinal +
                                                   ", name: " + name + ')');
            }
        }

        constants.add(this);

        this.name = name;
        this.ordinal = ordinal;
    }

    public static <E extends AbstractSafeEnum<E> & Extensions <E>> E valueOf(final Class <E> safeEnumType, final String name) {
        NullArgumentException.requireNonNull(safeEnumType, "valueOf", "safeEnumType");
        NullArgumentException.requireNonNull(name, "valueOf", "name");

        if (AbstractSafeEnum.class.equals(safeEnumType) || !AbstractSafeEnum.class.isAssignableFrom(safeEnumType)) {
            throw new IllegalArgumentException("Argument for safeEnumType must be a subclass of AbstractSafeEnum " +
                                               "(safeEnumType: " + safeEnumType + ')');
        }

        final List <? extends AbstractSafeEnum<?>> constants = subclassMap.get(safeEnumType);
        if (null == constants) {
            throw new IllegalStateException("Attempt to retrieve value from AbstractSafeEnum subclass that has not yet been " +
                                            "registered with the AbstractSafeEnum superclass via constructing at least one subclass constant");
        }

        for (final AbstractSafeEnum<?> constant : constants) {
            if (constant.name.equals(name)) {
                @SuppressWarnings("unchecked")
                final E e = (E)constant;
                return e;
            }
        }

        throw new IllegalArgumentException("Attempt to retrieve nonexistent value from AbstractSafeEnum subclass");
    }

    public static <E extends AbstractSafeEnum<E> & Extensions <E>> E[] values(final Class <E> safeEnumType) {
        NullArgumentException.requireNonNull(safeEnumType, "values", "safeEnumType");

        if (AbstractSafeEnum.class.equals(safeEnumType) || !AbstractSafeEnum.class.isAssignableFrom(safeEnumType)) {
            throw new IllegalArgumentException("Argument for safeEnumType must be a subclass of AbstractSafeEnum " +
                                               "(safeEnumType: " + safeEnumType + ')');
        }

        final List <? extends AbstractSafeEnum<?>> constants = subclassMap.get(safeEnumType);
        if (null == constants) {
            throw new IllegalStateException("Attempt to retrieve values for AbstractSafeEnum subclass that has not yet been " +
                                            "registered with the AbstractSafeEnum superclass via constructing at least one subclass constant");
        }

        @SuppressWarnings("unchecked")
        final E[] result = (E[])Array.newInstance(safeEnumType, constants.size());
        return result;
    }

    public final String name() {
        return name;
    }

    public static <K extends AbstractSafeEnum<K> & Extensions <K>, V> Map <K, V> map(final Class <K> safeEnumType,
                                                                                     final List <V> values) {
        NullArgumentException.requireNonNull(safeEnumType, "map", "safeEnumType");
        NullArgumentException.requireNonNull(values, "map", "values");

        if (AbstractSafeEnum.class.equals(safeEnumType) || !AbstractSafeEnum.class.isAssignableFrom(safeEnumType)) {
            throw new IllegalArgumentException("Argument for safeEnumType must be a subclass of AbstractSafeEnum " +
                                               "(safeEnumType: " + safeEnumType + ')');
        }

        final List <? extends AbstractSafeEnum<?>> constants = subclassMap.get(safeEnumType);
        if (null == constants) {
            throw new IllegalStateException("Attempt to retrieve mapping of constants to values for AbstractSafeEnum subclass " +
                                            "that has not yet been registered with the AbstractSafeEnum superclass via " +
                                            "constructing at least one subclass constant");
        }
        if (constants.size() != values.size()) {
            throw new IllegalArgumentException("values.size() is different from number of constant values in AbstractSafeEnum subclass " +
                                               "(num constants: " + constants.size() + ", values.size(): " + values.size() + ')');
        }

        final Map <K, V> result = new HashMap <>();

        for (int i = 0; i < constants.size(); ++i) {
            @SuppressWarnings("unchecked")
            final K k = (K)constants.get(i);
            result.put(k, values.get(i));
        }

        return result;
    }

    public final int ordinal() {
        return ordinal;
    }

    @Override
    public final int compareTo(final AbstractSafeEnum<E> o) {
        NullArgumentException.requireNonNull(o, "compareTo", "o");
        if (o.getClass() != this.getClass()) {
            throw new ClassCastException();
        }
        return this.ordinal - o.ordinal;
    }

    //Made final
    @Override
    public final boolean equals(final Object obj) {
        return this == obj;
    }

    //Made final
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    //TODO: Override finalize()?

    @Override
    public String toString() {
        return name;
    }

    @SuppressWarnings("unchecked")
    public final Class <E> getDeclaringClass() {
        /*
         * Find the direct subclass of AbstractSafeEnum that 'this' either is or extends
         * Suppose class A is a subclass of AbstractSafeEnum. If one of A's constants is
         * a subclass of A, the following code will still return A.class;
         */
        Class <E> clazz = (Class <E>)getClass();
        while (!clazz.getSuperclass().equals(AbstractSafeEnum.class)) {
            clazz = (Class <E>)clazz.getSuperclass();
        }
        return clazz;
    }

    @Override
    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("AbstractSafeEnum constants cannot be cloned");
    }

    /*
     * In an ideal world, there would be static abstract methods, but no such world exists.
     * The following interface ensures that the methods values(), valueOf(), and map() are
     * shadowed in implementing classes to perform the intended implementing class behavior.
     * The methods should be shadowed by implementing classes of this interface so that they
     * execute the intended behavior when invoked. They should never be invoked on this
     * interface (that would result in an UnsupportedOperationException). They should only
     * be invoked upon implementing classes of this interface. If one attempts to invoke one
     * of the methods on an implementing class of Extensions and the methods have not been
     * shadowed, a compiler error will happen, stating that the static method must be invoked
     * on the Extensions interface. As such, assuming a method is called at least once, errors
     * at compile-time and runtime can ensure that the method will be shadowed.
     */
    @SuppressWarnings("unused") //E is unused other than to bound type of implementing classes
    public interface Extensions <E extends AbstractSafeEnum<E> & Extensions <E>> {
        static <E extends AbstractSafeEnum<E> & Extensions <E>> E[] values() {
            throw new UnsupportedOperationException("values() must be invoked on an implementing class of AbstractSafeEnum.Extensions");
        }

        static <E extends AbstractSafeEnum<E> & Extensions <E>> E valueOf(@SuppressWarnings("unused") final String name) {
            throw new UnsupportedOperationException("valueOf() must be invoked on an implementing class of AbstractSafeEnum.Extensions");
        }

        static <K extends AbstractSafeEnum<K> & Extensions <K>, V> Map <K, V> map(@SuppressWarnings("unused") final List <V> values) {
            throw new UnsupportedOperationException("map() must be invoked on an implementing class of AbstractSafeEnum.Extensions");
        }
    }

    public interface Valuable <E extends AbstractSafeEnum<E> & Extensions <E> & Valuable <E>> {
        E value();
    }
}