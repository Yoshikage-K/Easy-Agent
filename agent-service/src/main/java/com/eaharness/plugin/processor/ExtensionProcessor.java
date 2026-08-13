package com.eaharness.plugin.processor;

import com.eaharness.plugin.annotation.Extension;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("com.eaharness.plugin.annotation.Extension")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ExtensionProcessor extends AbstractProcessor {
    private final Set<String> extensions = new LinkedHashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Extension.class)) {
            if (element instanceof TypeElement type) {
                extensions.add(type.getQualifiedName().toString());
            }
        }
        if (roundEnvironment.processingOver() && !extensions.isEmpty()) {
            try {
                Filer filer = processingEnv.getFiler();
                try (Writer writer = filer.createResource(
                        StandardLocation.CLASS_OUTPUT, "", "META-INF/eaharness-extensions.idx").openWriter()) {
                    for (String extension : extensions) {
                        writer.write(extension);
                        writer.write("\n");
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write extension index", exception);
            }
        }
        return true;
    }
}
