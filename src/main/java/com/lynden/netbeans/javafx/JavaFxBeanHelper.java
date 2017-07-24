/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Lynden, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.lynden.netbeans.javafx;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.embed.swing.JFXPanel;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class JavaFxBeanHelper implements CodeGenerator {

    private static final Set<String> SUPPORTED_CLASSES = new HashSet<>();

    static {
        SUPPORTED_CLASSES.add("javafx.beans.property.IntegerProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.LongProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.FloatProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.DoubleProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.BooleanProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.StringProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ListProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.SetProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.MapProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ObjectProperty");

        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyIntegerProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyLongProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyFloatProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyDoubleProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyBooleanProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyStringProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyListProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlySetProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyMapProperty");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyObjectProperty");

        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyIntegerWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyLongWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyFloatWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyDoubleWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyBooleanWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyStringWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyListWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlySetWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyMapWrapper");
        SUPPORTED_CLASSES.add("javafx.beans.property.ReadOnlyObjectWrapper");
    }

    protected JTextComponent textComponent;
    //this is needed to initialize the JavaFx Toolkit
    protected JFXPanel panel = new JFXPanel();
    protected List<VariableElement> fields;

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private JavaFxBeanHelper(Lookup context) { // Good practice is not to save Lookup outside ctor
        textComponent = context.lookup(JTextComponent.class);
        CompilationController controller = context.lookup(CompilationController.class);
        try {
            fields = getFields(context, controller);
        } catch (CodeGeneratorException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @MimeRegistration(mimeType = "text/x-java", position = 250, service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        @Override
        public List<? extends CodeGenerator> create(Lookup context) {
            return Collections.singletonList(new JavaFxBeanHelper(context));
        }
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    @Override
    public String getDisplayName() {
        return "Java FX Getter and Setter...";
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    @Override
    public void invoke() {
        Document doc = textComponent.getDocument();
        JavaSource javaSource = JavaSource.forDocument(doc);

        CancellableTask<WorkingCopy> task = new CodeGeneratorCancellableTask(textComponent) {
            @Override
            public void generateCode(WorkingCopy workingCopy, TreePath path, int position) {
                JavaFxBeanHelper.this.generateCode(workingCopy, path, position, JavaFxBeanHelper.this.fields);
            }
        };

        try {
            ModificationResult result = javaSource.runModificationTask(task);
            result.commit();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    protected void generateCode(WorkingCopy wc, TreePath path, int position, List<VariableElement> fields) {

        TypeElement typeClassElement = (TypeElement) wc.getTrees().getElement(path);
        if (typeClassElement != null) {

            TreeMaker make = wc.getTreeMaker();
            ClassTree classTree = (ClassTree) path.getLeaf();
            List<Tree> members = new ArrayList<>(classTree.getMembers());

            PropertyMethodBuilder propertyMethodBuilder = new PropertyMethodBuilder(make, fields);

            List<MethodTree> createdMethods = propertyMethodBuilder.createPropMethods();

            /* Filtering out methods that might clash with the pre-existing
             * ones. */
            createdMethods.removeIf((MethodTree createdMethod) -> {
                return TreeHelper.hasMethodWithSameName(members, createdMethod);
            });

            members.addAll(position, createdMethods);

            ClassTree newClassTree = make.Class(
                    classTree.getModifiers(),
                    classTree.getSimpleName(),
                    classTree.getTypeParameters(),
                    classTree.getExtendsClause(),
                    classTree.getImplementsClause(),
                    members);

            wc.rewrite(classTree, newClassTree);
        }
    }

    private List<VariableElement> getFields(Lookup context, CompilationController controller) throws CodeGeneratorException {
        try {
            TreePath treePath = context.lookup(TreePath.class);
            TreePath path = controller.getTreeUtilities().getPathElementOfKind(EnumSet.of(Tree.Kind.CLASS, Tree.Kind.ENUM), treePath);
            TypeElement typeElement = (TypeElement) controller.getTrees().getElement(path);

            if (!typeElement.getKind().isClass()) {
                throw new CodeGeneratorException("typeElement " + typeElement.getKind().name() + " is not a class, cannot generate code.");
            }

            Elements elements = controller.getElements();
            List<VariableElement> allFields = ElementFilter.fieldsIn(elements.getAllMembers(typeElement));

            List<VariableElement> supportedFields = allFields.stream().filter((VariableElement var) -> {
                return SUPPORTED_CLASSES.contains(TypeHelper.getClassName(var.asType().toString()));
            }).collect(Collectors.toList());

            return supportedFields;
        } catch (NullPointerException ex) {
            throw new CodeGeneratorException(ex);
        }
    }

    private static class CodeGeneratorException extends Exception {

        private static final long serialVersionUID = 1L;

        public CodeGeneratorException(String message) {
            super(message);
        }

        public CodeGeneratorException(Throwable cause) {
            super(cause);
        }
    }

}
