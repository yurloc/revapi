/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

import java.util.List;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.revapi.ElementMatcher;
import org.revapi.ElementMatcher.Result;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class ReturnsExpression implements MatchExpression {
    private final MatchExpression returnTypeMatch;
    private final boolean covariant;

    ReturnsExpression(MatchExpression returnTypeMatch, boolean covariant) {
        this.returnTypeMatch = returnTypeMatch;
        this.covariant = covariant;
    }

    @Override
    public Result matches(JavaModelElement element) {
        if (!(element instanceof JavaMethodElement)) {
            return Result.DOESNT_MATCH;
        }

        JavaMethodElement methodElement = (JavaMethodElement) element;

        TypeEnvironment typeEnvironment = element.getTypeEnvironment();

        TypeMirror rt = methodElement.getModelRepresentation().getReturnType();
        if (rt.getKind() == TypeKind.VOID || rt.getKind().isPrimitive()) {
            return returnTypeMatch.matches(rt);
        }

        JavaTypeElement returnType = typeEnvironment.getModelElement(rt);

        Result ret = returnTypeMatch.matches(returnType);
        if (ret == Result.MATCH) {
            return ret;
        }

        if (covariant) {
            List<TypeMirror> superTypes = Util.getAllSuperTypes(typeEnvironment.getTypeUtils(), rt);
            ret = superTypes.stream().reduce(Result.DOESNT_MATCH,
                    (partial, t) -> {
                        JavaTypeElement type = typeEnvironment.getModelElement(t);
                        if (type == null) {
                            return partial.or(Result.DOESNT_MATCH);
                        } else {
                            return partial.or(returnTypeMatch.matches(type));
                        }
                    },
                    Result::or);
        }

        return ret;
    }

    @Override
    public Result matches(JavaAnnotationElement annotation) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(AnnotationAttributeElement attribute) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(TypeParameterElement typeParameter) {
        return Result.DOESNT_MATCH;
    }
}