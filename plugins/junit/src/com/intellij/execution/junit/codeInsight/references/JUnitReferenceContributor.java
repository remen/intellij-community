// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit.codeInsight.references;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public class JUnitReferenceContributor extends PsiReferenceContributor {
  private static PsiElementPattern.Capture<PsiElement> getElementPattern(String annotation, String paramName) {
    return PlatformPatterns.psiElement(PsiElement.class).and(new FilterPattern(new TestAnnotationFilter(annotation, paramName)));
  }

  private static PsiElementPattern.Capture<PsiElement> getEnumSourceNamesPattern() {
    return getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE, "names")
      .withAncestor(4, PlatformPatterns.psiElement(PsiAnnotation.class).and(new PsiJavaElementPattern<>(new InitialPatternCondition<PsiAnnotation>(PsiAnnotation.class) {
        @Override
        public boolean accepts(@Nullable Object o, ProcessingContext context) {
          if (o instanceof PsiAnnotation) {
            PsiAnnotationMemberValue mode = ((PsiAnnotation)o).findAttributeValue("mode");
            if (mode instanceof PsiReferenceExpression) {
              String referenceName = ((PsiReferenceExpression)mode).getReferenceName();
              return "INCLUDE".equals(referenceName) || "EXCLUDE".equals(referenceName);
            }
          }
          return false;
        }
      })));
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE, "value"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new MethodSourceReference[]{new MethodSourceReference(UastContextKt.toUElement(element, UExpression.class), (PsiLanguageInjectionHost)element)};
      }
    });
    registrar.registerReferenceProvider(getEnumSourceNamesPattern(), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new EnumSourceReference[] {new EnumSourceReference((PsiLiteral)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE, "resources"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return FileReferenceSet.createSet(element, false, false, false).getAllReferences();
      }
    });
  }

  private static class TestAnnotationFilter implements ElementFilter {

    private final String myAnnotation;
    private final String myParameterName;

    TestAnnotationFilter(String annotation, @NotNull @NonNls String parameterName) {
      myAnnotation = annotation;
      myParameterName = parameterName;
    }

    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      UElement type = UastContextKt.toUElement(context, UElement.class);
      if (type == null) return false;
      UNamedExpression uPair = UastUtils.getParentOfType(type, UNamedExpression.class);
      if (uPair == null) return false;
      String name = ObjectUtils.notNull(uPair.getName(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (!myParameterName.equals(name)) return false;
      UAnnotation uAnnotation = UastUtils.getParentOfType(type, UAnnotation.class);
      if (uAnnotation == null) return false;
      return myAnnotation.equals(uAnnotation.getQualifiedName());
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return PsiLanguageInjectionHost.class.isAssignableFrom(hintClass);
    }
  }
}
