package org.junit.experimental.theories;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.experimental.theories.internal.Assignments;
import org.junit.experimental.theories.internal.FrameworkMethodWithAssignments;
import org.junit.experimental.theories.internal.ParameterizedAssertionError;
import org.junit.experimental.theories.internal.TheoriesBase;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * The Theories runner allows to test a certain functionality against a subset of an infinite set of data points.
 * <p>
 * A Theory is a piece of functionality (a method) that is executed against several data inputs called data points.
 * To make a test method a theory you mark it with <b>&#064;Theory</b>. To create a data point you create a public
 * field in your test class and mark it with <b>&#064;DataPoint</b>. The Theories runner then executes your test
 * method as many times as the number of data points declared, providing a different data point as
 * the input argument on each invocation.
 * </p>
 * <p>
 * A Theory differs from standard test method in that it captures some aspect of the intended behavior in possibly
 * infinite numbers of scenarios which corresponds to the number of data points declared. Using assumptions and
 * assertions properly together with covering multiple scenarios with different data points can make your tests more
 * flexible and bring them closer to scientific theories (hence the name).
 * </p>
 * <p>
 * For example:
 * <pre>
 *
 * &#064;RunWith(<b>Theories.class</b>)
 * public class UserTest {
 *      <b>&#064;DataPoint</b>
 *      public static String GOOD_USERNAME = "optimus";
 *      <b>&#064;DataPoint</b>
 *      public static String USERNAME_WITH_SLASH = "optimus/prime";
 *
 *      <b>&#064;Theory</b>
 *      public void filenameIncludesUsername(String username) {
 *          assumeThat(username, not(containsString("/")));
 *          assertThat(new User(username).configFileName(), containsString(username));
 *      }
 * }
 * </pre>
 * This makes it clear that the user's filename should be included in the config file name,
 * only if it doesn't contain a slash. Another test or theory might define what happens when a username does contain
 * a slash. <code>UserTest</code> will attempt to run <code>filenameIncludesUsername</code> on every compatible data
 * point defined in the class. If any of the assumptions fail, the data point is silently ignored. If all of the
 * assumptions pass, but an assertion fails, the test fails.
 * <p>
 * Defining general statements as theories allows data point reuse across a bunch of functionality tests and also
 * allows automated tools to search for new, unexpected data points that expose bugs.
 * </p>
 * <p>
 * The support for Theories has been absorbed from the Popper project, and more complete documentation can be found
 * from that projects archived documentation.
 * </p>
 *
 * @see <a href="http://web.archive.org/web/20071012143326/popper.tigris.org/tutorial.html">Archived Popper project documentation</a>
 * @see <a href="http://web.archive.org/web/20110608210825/http://shareandenjoy.saff.net/tdd-specifications.pdf">Paper on Theories</a>
 */
public class ExpandedTheories extends TheoriesBase {

    public ExpandedTheories(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        List<FrameworkMethod> testMethods = super.computeTestMethods();

        List<FrameworkMethod> testMethodsWithAssignments = new ArrayList<FrameworkMethod>();
        for(FrameworkMethod method : testMethods) {
            Collection<Assignments> assignments = computeAssignments(method);
            for(Assignments assignment : assignments) {
                testMethodsWithAssignments.add(new FrameworkMethodWithAssignments(method.getMethod(), assignment));
            }
        }

        return testMethodsWithAssignments;
    }

    protected Collection<Assignments> computeAssignments(FrameworkMethod method) {
        Collection<Assignments> assignments = new ArrayList<Assignments>();
        recursivelyComputeAssignments(Assignments.allUnassigned(method.getMethod(), getTestClass()), assignments);
        return assignments;
    }

    protected void recursivelyComputeAssignments(Assignments parameterAssignment, Collection<Assignments> assignments) {
        if (!parameterAssignment.isComplete()) {
            try {
                for (PotentialAssignment source : parameterAssignment.potentialsForNextUnassigned()) {
                    recursivelyComputeAssignments(parameterAssignment.assignNext(source), assignments);
                }
            } catch (Throwable t) {
                // FIXME
                t.printStackTrace();
            }
        } else {
            assignments.add(parameterAssignment);
        }
    }

    private Assignments extractAssignments(FrameworkMethod method) {
        if (method instanceof FrameworkMethodWithAssignments) {
            return ((FrameworkMethodWithAssignments) method).getAssignments();
        }
        return Assignments.allUnassigned(method.getMethod(), getTestClass());
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        return methodCompletesWithParameters(method, extractAssignments(method), test);
    }

    @Override
    public Statement methodBlock(final FrameworkMethod method) {
        final Statement statement = super.methodBlock(method);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    statement.evaluate();
                } catch (AssumptionViolatedException e) {
                    throw e;
                } catch (Throwable e) {
                    reportParameterizedError(method, e, extractAssignments(method)
                            .getArgumentStrings(nullsOk(method)));
                }
            }

        };
    }

    private Statement methodCompletesWithParameters(
            final FrameworkMethod method, final Assignments complete, final Object freshInstance) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Object[] values = complete.getMethodArguments();

                if (!nullsOk(method)) {
                    Assume.assumeNotNull(values);
                }

                method.invokeExplosively(freshInstance, values);
            }
        };
    }

    @Override
    protected Object createTest(FrameworkMethod method) throws Exception {
        Object[] params = extractAssignments(method).getConstructorArguments();

        if (!nullsOk(method)) {
            Assume.assumeNotNull(params);
        }

        return getTestClass().getOnlyConstructor().newInstance(params);
    }

    protected void reportParameterizedError(FrameworkMethod testMethod, Throwable e, Object... params)
            throws Throwable {
        if (params.length == 0) {
            throw e;
        }
        throw new ParameterizedAssertionError(e, testMethod.getName(), params);
    }

    private boolean nullsOk(FrameworkMethod testMethod) {
        Theory annotation = testMethod.getMethod().getAnnotation(Theory.class);
        if (annotation == null) {
            return false;
        }
        return annotation.nullsAccepted();
    }

}
