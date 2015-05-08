package org.junit.experimental.theories.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.junit.experimental.theories.PotentialAssignment.CouldNotGenerateValueException;
import org.junit.runners.model.FrameworkMethod;

public class FrameworkMethodWithAssignments extends FrameworkMethod {
    private Assignments assignments;

    public FrameworkMethodWithAssignments(Method method, Assignments assignments) {
        super(method);
        this.assignments = assignments;
    }

    public Assignments getAssignments() {
        return assignments;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FrameworkMethodWithAssignments)) {
            return false;
        }
        FrameworkMethodWithAssignments other = (FrameworkMethodWithAssignments) o;
        return getMethod().equals(other.getMethod()) && getAssignments().equals(other.getAssignments());
    }

    @Override
    public int hashCode() {
        return getMethod().hashCode() + getAssignments().hashCode();
    }

    @Override
    public String getName() {
        try {
            return String.format("%s(%s)", super.getName(), joinAssignments(", "));
        } catch (CouldNotGenerateValueException e) {
            return super.getName() + "([Could not generate test input value strings])";
        }
    }


    private String joinAssignments(String delimiter) throws CouldNotGenerateValueException {
        Collection<Object> values = Arrays.asList(getAssignments().getArgumentStrings(true));
        StringBuilder sb = new StringBuilder();
        Iterator<Object> iter = values.iterator();
        while (iter.hasNext()) {
            Object next = iter.next();
            sb.append(stringValueOf(next));
            if (iter.hasNext()) {
                sb.append(delimiter);
            }
        }
        return sb.toString();
    }

    private static String stringValueOf(Object next) {
        try {
            return String.valueOf(next);
        } catch (Throwable e) {
            return "[toString failed]";
        }
    }
}
