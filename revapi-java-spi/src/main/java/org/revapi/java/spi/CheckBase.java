/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java.spi;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

import org.revapi.AnalysisContext;
import org.revapi.Difference;

/**
 * A basic implementation of the {@link Check} interface. This class easies the matching of the {@code visit*()}
 * methods and their corresponding {@link #visitEnd()} by keeping track of the "depth" individual calls (see the
 * recursive
 * nature of the {@link org.revapi.java.spi.Check call order}).
 * 
 * <p>This class also contains a couple of utility methods for checking the accessibility of elements, etc.
 *
 * @author Lukas Krejci
 * @see #pushActive(JavaElement, JavaElement, Object...)
 * @see #popIfActive()
 * @since 0.1
 */
public abstract class CheckBase implements Check {

    /**
     * Checks whether both provided elements are (package) private. If one of them is null, the fact cannot be
     * determined and therefore this method would return false.
     *
     * @param a first element
     * @param b second element
     * @return true if both elements are not null and are private or package private
     */
    public boolean isBothPrivate(@Nullable JavaModelElement a, @Nullable JavaModelElement b) {
        if (a == null || b == null) {
            return false;
        }

        return !isAccessible(a) && !isAccessible(b);
    }

    /**
     * Checks whether both provided elements are public or protected. If one at least one of them is null, the method
     * returns false, because the accessibility cannot be truthfully detected in that case.
     *
     * @param a first element
     * @param b second element
     * @return true if both elements are not null and accessible (i.e. public or protected)
     */
    public boolean isBothAccessible(@Nullable JavaModelElement a, @Nullable JavaModelElement b) {
        if (a == null || b == null) {
            return false;
        }

        return isAccessible(a) && isAccessible(b);
    }

    /**
     * @param e the element to check
     * @return true if the provided element is public or protected, false otherwise.
     */
    public boolean isAccessible(@Nonnull JavaModelElement e) {
        if (!isAccessibleByModifier(e.getDeclaringElement())) {
            return false;
        }

        JavaModelElement parent = e.getParent();

        if (e instanceof JavaTypeElement) {
            return ((JavaTypeElement) e).isInAPI() && (parent == null || isAccessible(parent));
        } else {
            assert parent != null;
            return isAccessible(parent);
        }
    }

    private boolean isAccessibleByModifier(Element e) {
        return !isMissing(e) && (e.getModifiers().contains(Modifier.PUBLIC) ||
                e.getModifiers().contains(Modifier.PROTECTED));
    }

    /**
     * The element is deemed missing if its type kind ({@link javax.lang.model.type.TypeMirror#getKind()}) is
     * {@link TypeKind#ERROR}.
     *
     * @param e the element
     *
     * @return true if the element is missing, false otherwise
     */
    public boolean isMissing(@Nonnull Element e) {
        return e.asType().getKind() == TypeKind.ERROR;
    }

    /**
     * Represents the elements that have been {@link #pushActive(JavaElement, JavaElement, Object...) pushed} onto
     * the active elements stack.
     *
     * @param <T> the type of elements
     */
    protected static class ActiveElements<T extends JavaElement> {
        public final T oldElement;
        public final T newElement;
        public final Object[] context;
        private final int depth;

        private ActiveElements(int depth, T oldElement, T newElement, Object... context) {
            this.depth = depth;
            this.oldElement = oldElement;
            this.newElement = newElement;
            this.context = context;
        }
    }

    private TypeEnvironment oldTypeEnvironment;
    private TypeEnvironment newTypeEnvironment;
    private int depth;
    private final Deque<ActiveElements<?>> activations = new ArrayDeque<>();
    private AnalysisContext analysisContext;

    @Nonnull
    protected Difference createDifference(@Nonnull Code code,
        Object... params) {

        return createDifference(code, params, params);
    }

    @Nonnull
    protected Difference createDifference(@Nonnull Code code, @Nullable Object[] params, Object... attachments) {
        return code.createDifference(getAnalysisContext().getLocale(), params, attachments);
    }

    @Nonnull
    public TypeEnvironment getOldTypeEnvironment() {
        return oldTypeEnvironment;
    }

    @Nonnull
    public TypeEnvironment getNewTypeEnvironment() {
        return newTypeEnvironment;
    }

    @Nonnull
    public AnalysisContext getAnalysisContext() {
        return analysisContext;
    }

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public void setOldTypeEnvironment(@Nonnull TypeEnvironment env) {
        oldTypeEnvironment = env;
    }

    @Override
    public void setNewTypeEnvironment(@Nonnull TypeEnvironment env) {
        newTypeEnvironment = env;
    }

    /**
     * Please override the {@link #doEnd()} method instead.
     *
     * @see org.revapi.java.spi.Check#visitEnd()
     */
    @Nullable
    @Override
    public final List<Difference> visitEnd() {
        try {
            return doEnd();
        } finally {
            //defensive pop if the doEnd "forgets" to do it.
            //this is to prevent accidental retrieval of wrong data in the case the last active element was pushed
            //by a "sibling" call which forgot to pop it. The current visit* + end combo would think it was active
            //even if the visit call didn't push anything to the stack.
            popIfActive();
            depth--;
        }
    }

    @Nullable
    protected List<Difference> doEnd() {
        return null;
    }

    /**
     * Please override the
     * {@link #doVisitClass(JavaTypeElement, JavaTypeElement)}
     *
     * @see Check#visitClass(JavaTypeElement, JavaTypeElement)
     */
    @Override
    public final void visitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
        depth++;
        doVisitClass(oldType, newType);
    }

    protected void doVisitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
    }

    /**
     * Please override the
     * {@link #doVisitMethod(JavaMethodElement, JavaMethodElement)}
     * instead.
     *
     * @see Check#visitMethod(JavaMethodElement, JavaMethodElement)
     */
    @Override
    public final void visitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        depth++;
        doVisitMethod(oldMethod, newMethod);
    }

    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
    }

    @Override
    public final void visitMethodParameter(@Nullable JavaMethodParameterElement oldParameter,
        @Nullable JavaMethodParameterElement newParameter) {
        depth++;
        doVisitMethodParameter(oldParameter, newParameter);
    }

    @SuppressWarnings("UnusedParameters")
    protected void doVisitMethodParameter(@Nullable JavaMethodParameterElement oldParameter,
        @Nullable JavaMethodParameterElement newParameter) {
    }

    /**
     * Please override the
     * {@link #doVisitField(JavaFieldElement, JavaFieldElement)}
     * instead.
     *
     * @see Check#visitField(JavaFieldElement, JavaFieldElement)
     */
    @Override
    public final void visitField(@Nullable JavaFieldElement oldField, @Nullable JavaFieldElement newField) {
        depth++;
        doVisitField(oldField, newField);
    }

    protected void doVisitField(@Nullable JavaFieldElement oldField, @Nullable JavaFieldElement newField) {
    }

    /**
     * Please override the
     * {@link #doVisitAnnotation(JavaAnnotationElement, JavaAnnotationElement)}
     * instead.
     *
     * @see Check#visitAnnotation(JavaAnnotationElement, JavaAnnotationElement)
     */
    @Nullable
    @Override
    public final List<Difference> visitAnnotation(@Nullable JavaAnnotationElement oldAnnotation,
        @Nullable JavaAnnotationElement newAnnotation) {
        depth++;
        List<Difference> ret = doVisitAnnotation(oldAnnotation, newAnnotation);
        depth--;
        return ret;
    }

    @Nullable
    protected List<Difference> doVisitAnnotation(@Nullable JavaAnnotationElement oldAnnotation,
        @Nullable JavaAnnotationElement newAnnotation) {
        return null;
    }

    /**
     * If called in one of the {@code doVisit*()} methods, this method will push the elements along with some
     * contextual
     * data onto an internal stack.
     * 
     * <p>You can then retrieve the contents on the top of the stack in your {@link #doEnd()} override by calling the
     * {@link #popIfActive()} method.
     *
     * @param oldElement the old API element
     * @param newElement the new API element
     * @param context    optional contextual data
     * @param <T>        the type of the elements
     */
    protected final <T extends JavaElement> void pushActive(@Nullable T oldElement, @Nullable T newElement,
        Object... context) {
        ActiveElements<T> r = new ActiveElements<>(depth, oldElement, newElement, context);
        activations.push(r);
    }

    /**
     * Pops the top of the stack of active elements if the current position in the call stack corresponds to the one
     * that pushed the active elements.
     * 
     * <p>This method does not do any type checks, so take care to retrieve the elements with the same types used to push
     * to them onto the stack.
     *
     * @param <T> the type of the elements
     *
     * @return the active elements or null if the current call stack did not push any active elements onto the stack
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T extends JavaElement> ActiveElements<T> popIfActive() {
        return (ActiveElements<T>) (!activations.isEmpty() && activations.peek().depth == depth ? activations.pop() :
            null);
    }
}
