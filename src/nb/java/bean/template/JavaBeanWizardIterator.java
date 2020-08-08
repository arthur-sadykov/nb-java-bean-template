/*
 * Copyright (c) 2020 Arthur Sadykov.
 */
package nb.java.bean.template;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import nb.java.bean.constants.ConstantDataManager;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.spi.java.project.support.ui.templates.JavaTemplates;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Arthur Sadykov
 */
@TemplateRegistration(
        folder = "Classes",
        displayName = "#JavaBeanIterator_displayName",
        content = "JavaBean.java.template",
        description = "java-bean.html",
        scriptEngine = "freemarker"
)
@Messages("JavaBeanIterator_displayName=Java Bean")
public class JavaBeanWizardIterator implements WizardDescriptor.InstantiatingIterator<WizardDescriptor> {

    private int index;
    private WizardDescriptor wizard;
    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;
    private JavaBeanWizardPanel javaBeanWizardPanel;
    private final Map<String, String> nameToTypeMap = new HashMap<>();

    private List<WizardDescriptor.Panel<WizardDescriptor>> getPanels() {
        if (panels == null) {
            panels = new ArrayList<>();
            Project project = Templates.getProject(wizard);
            Sources sources = ProjectUtils.getSources(project);
            SourceGroup[] groups = sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
            WizardDescriptor.Panel<WizardDescriptor> packageChooserPanel =
                    JavaTemplates.createPackageChooser(project, groups);
            panels.add(packageChooserPanel);
            javaBeanWizardPanel = new JavaBeanWizardPanel();
            panels.add(javaBeanWizardPanel);
            String[] steps = createSteps();
            for (int i = 0; i < panels.size(); i++) {
                Component c = panels.get(i).getComponent();
                if (steps[i] == null) {
                    // Default step name to component name of panel. Mainly
                    // useful for getting the name of the target chooser to
                    // appear in the list of steps.
                    steps[i] = c.getName();
                }
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                    jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
                }
            }
        }
        return Collections.unmodifiableList(panels);
    }

    @Override public Set<?> instantiate() throws IOException {
        FileObject template = Templates.getTemplate(wizard);
        FileObject targetFolder = Templates.getTargetFolder(wizard);
        DataFolder targetDataFolder = DataFolder.findFolder(targetFolder);
        DataObject templateDataObject = DataObject.find(template);
        DataObject createdDataObject = templateDataObject.createFromTemplate(targetDataFolder,
                Templates.getTargetName(wizard));
        JavaSource javaSource = JavaSource.forFileObject(createdDataObject.getPrimaryFile());
        if (javaSource == null) {
            throw new IllegalStateException(ConstantDataManager.NO_ASSOCIATED_JAVA_SOURCE);
        }
        try {
            javaSource.runModificationTask(workingCopy -> {
                workingCopy.toPhase(JavaSource.Phase.RESOLVED);
                List<? extends TypeElement> topLevelElements = workingCopy.getTopLevelElements();
                TypeElement clazz = null;
                for (TypeElement topLevelElement : topLevelElements) {
                    if (topLevelElement.getKind() == ElementKind.CLASS) {
                        clazz = topLevelElement;
                        break;
                    }
                }
                if (generateEquals()) {
                    CompilationUnitTree compilationUnit = workingCopy.getCompilationUnit();
                    CompilationUnitTree newCompilationUnit = addImports(workingCopy);
                    workingCopy.rewrite(compilationUnit, newCompilationUnit);
                }
                extractFieldDefinitions();
                ClassTree oldClassTree = getClassTree(workingCopy);
                ClassTree newClassTree = addFieldsToClass(workingCopy, oldClassTree);
                if (generateDefaultConstructor()) {
                    newClassTree = addDefaultConstructorToClass(workingCopy, newClassTree);
                }
                if (generateGetters()) {
                    newClassTree = addGettersToClass(workingCopy, newClassTree);
                }
                if (generateSetters()) {
                    newClassTree = addSettersToClass(workingCopy, newClassTree);
                }
                if (generateEquals()) {
                    newClassTree = addEqualsMethodToClass(workingCopy, clazz, newClassTree);
                }
                if (generateHashCode()) {
                    newClassTree = addHashCodeMethodToClass(workingCopy, newClassTree);
                }
                if (generateToString()) {
                    newClassTree = addToStringMethodToClass(workingCopy, clazz, newClassTree);
                }
                workingCopy.rewrite(oldClassTree, newClassTree);
            }).commit();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Collections.singleton(createdDataObject.getPrimaryFile());
    }

    private boolean generateGetters() {
        Object generateGetters = wizard.getProperty(ConstantDataManager.GENERATE_GETTERS_PROPERTY);
        return generateGetters == null ? false : (boolean) generateGetters;
    }

    private boolean generateSetters() {
        Object generateSetters = wizard.getProperty(ConstantDataManager.GENERATE_SETTERS_PROPERTY);
        return generateSetters == null ? false : (boolean) generateSetters;
    }

    private boolean generateEquals() {
        Object generateEquals = wizard.getProperty(ConstantDataManager.GENERATE_EQUALS_PROPERTY);
        return generateEquals == null ? false : (boolean) generateEquals;
    }

    private boolean generateHashCode() {
        Object generateHashCode = wizard.getProperty(ConstantDataManager.GENERATE_HASH_CODE_PROPERTY);
        return generateHashCode == null ? false : (boolean) generateHashCode;
    }

    private boolean generateToString() {
        Object generateToString = wizard.getProperty(ConstantDataManager.GENERATE_TO_STRING_PROPERTY);
        return generateToString == null ? false : (boolean) generateToString;
    }

    private boolean generateDefaultConstructor() {
        Object generateDefaultConstructor = wizard.getProperty(ConstantDataManager.GENERATE_DEFAULT_CONSTRUCTOR);
        return generateDefaultConstructor == null ? false : (boolean) generateDefaultConstructor;
    }

    private CompilationUnitTree addImports(WorkingCopy workingCopy) {
        CompilationUnitTree compilationUnitTree = workingCopy.getCompilationUnit();
        TreeMaker make = workingCopy.getTreeMaker();
        List<? extends ImportTree> imports = compilationUnitTree.getImports();
        boolean objectsImportFound = false;
        for (int i = 0; i < imports.size(); i++) {
            if (imports.get(i).getQualifiedIdentifier().toString().equals(ConstantDataManager.OBJECTS_TYPE)) {
                objectsImportFound = true;
                break;
            }
        }
        if (!objectsImportFound) {
            compilationUnitTree =
                    make.addCompUnitImport(compilationUnitTree,
                            make.Import(make.Identifier(ConstantDataManager.OBJECTS_TYPE), false));
        }
        return compilationUnitTree;
    }

    private void extractFieldDefinitions() {
        JavaBeanVisualPanel visualPanel = javaBeanWizardPanel.getComponent();
        List<FieldPanel> fieldPanels = visualPanel.getFieldPanels();
        fieldPanels.forEach(panel -> {
            String fieldName = panel.getFieldName();
            String fieldType = panel.getFieldType();
            nameToTypeMap.put(fieldName, fieldType);
        });
    }

    private ClassTree getClassTree(WorkingCopy workingCopy) {
        CompilationUnitTree compilationUnitTree = workingCopy.getCompilationUnit();
        List<? extends Tree> typeDeclarations = compilationUnitTree.getTypeDecls();
        ClassTree classTree = null;
        for (Tree typeDeclaration : typeDeclarations) {
            if (typeDeclaration.getKind() == Kind.CLASS) {
                classTree = (ClassTree) typeDeclaration;
                break;
            }
        }
        if (classTree == null) {
            throw new IllegalStateException(ConstantDataManager.CLASS_NOT_PRESENT);
        }
        return classTree;
    }

    private ClassTree addFieldsToClass(WorkingCopy workingCopy, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        for (Map.Entry<String, String> entry : nameToTypeMap.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            newClassTree = make.addClassMember(
                    newClassTree,
                    make.Variable(
                            make.Modifiers(Collections.singleton(Modifier.PRIVATE)),
                            fieldName,
                            make.Type(fieldType),
                            null));
        }
        return newClassTree;
    }

    private ClassTree addDefaultConstructorToClass(WorkingCopy workingCopy, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        newClassTree = make.addClassMember(newClassTree,
                make.Constructor(make.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        make.Block(Arrays.asList(make.ExpressionStatement(make.MethodInvocation(Collections.emptyList(),
                                make.Identifier(ConstantDataManager.SUPER),
                                Collections.emptyList()))),
                                false)));
        return newClassTree;
    }

    private ClassTree addGettersToClass(WorkingCopy workingCopy, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        for (Map.Entry<String, String> entry : nameToTypeMap.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            ModifiersTree modifiersTree = make.Modifiers(Collections.singleton(Modifier.PUBLIC));
            String prefix = fieldType.equals(ConstantDataManager.BOOLEAN_TYPE)
                    || fieldType.equals(ConstantDataManager.BOOLEAN)
                    || fieldType.equals(ConstantDataManager.SIMPLE_WRAPPER_BOOLEAN_TYPE)
                    ? ConstantDataManager.IS_PREFIX
                    : ConstantDataManager.GET_PREFIX;
            String capitalizedFieldName = capitalize(fieldName);
            String methodName = prefix.concat(capitalizedFieldName);
            newClassTree =
                    make.addClassMember(
                            newClassTree,
                            make.Method(
                                    modifiersTree,
                                    methodName,
                                    make.Type(fieldType),
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    make.Block(Arrays.asList(make.Return(make.Identifier(fieldName))),
                                            false),
                                    null));
        }
        return newClassTree;
    }

    private ClassTree addSettersToClass(WorkingCopy workingCopy, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        for (Map.Entry<String, String> entry : nameToTypeMap.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            ModifiersTree modifiersTree = make.Modifiers(Collections.singleton(Modifier.PUBLIC));
            String capitalizedFieldName = capitalize(fieldName);
            String prefix = ConstantDataManager.SET_PREFIX;
            String methodName = prefix.concat(capitalizedFieldName);
            MemberSelectTree variable = make.MemberSelect(make.Identifier(ConstantDataManager.THIS), fieldName);
            AssignmentTree expression = make.Assignment(variable, make.Identifier(fieldName));
            ExpressionStatementTree setStatement = make.ExpressionStatement(expression);
            newClassTree =
                    make.addClassMember(newClassTree,
                            make.Method(modifiersTree,
                                    methodName,
                                    make.Type(ConstantDataManager.VOID_TYPE),
                                    Collections.emptyList(),
                                    Arrays.asList(make.Variable(make.Modifiers(Collections.emptySet()),
                                            fieldName, make.Type(fieldType), null)),
                                    Collections.emptyList(),
                                    make.Block(Arrays.asList(setStatement), false),
                                    null));
        }
        return newClassTree;
    }

    private String capitalize(String string) {
        if (string.isEmpty()) {
            return string;
        }
        return string.substring(0, 1).toUpperCase().concat(string.substring(1));
    }

    private ClassTree addEqualsMethodToClass(WorkingCopy workingCopy, TypeElement type, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        Set<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
        TypeElement objectTypeElement = workingCopy.getElements().getTypeElement(ConstantDataManager.OBJECT_TYPE);
        List<VariableTree> params =
                Collections.singletonList(make.Variable(make.Modifiers(EnumSet.noneOf(Modifier.class)), ConstantDataManager.OBJECT,
                        objectTypeElement != null
                                ? make.Type(objectTypeElement.asType())
                                : make.Identifier(ConstantDataManager.SIMPLE_OBJECT_TYPE_NAME),
                        null));
        List<StatementTree> statements = new ArrayList<>();
        statements.add(make.If(make.Binary(Tree.Kind.EQUAL_TO, make.Identifier(ConstantDataManager.THIS), make.Identifier(ConstantDataManager.OBJECT)),
                make.Return(make.Identifier(ConstantDataManager.TRUE)), null));
        statements.add(make.If(make.Binary(Tree.Kind.EQUAL_TO, make.Identifier(ConstantDataManager.OBJECT), make.Identifier(ConstantDataManager.NULL)),
                make.Return(make.Identifier(ConstantDataManager.FALSE)), null));
        statements.add(make.If(make.Binary(Tree.Kind.NOT_EQUAL_TO,
                make.MethodInvocation(Collections.<ExpressionTree>emptyList(), make.Identifier(ConstantDataManager.GET_CLASS_METHOD_NAME),
                        Collections.<ExpressionTree>emptyList()), make.MethodInvocation(Collections.<ExpressionTree>emptyList(), make.MemberSelect(make.Identifier(ConstantDataManager.OBJECT), ConstantDataManager.GET_CLASS_METHOD_NAME),
                Collections.<ExpressionTree>emptyList())), make.Return(make.Identifier(ConstantDataManager.FALSE)), null));
        statements.add(make.Variable(make.Modifiers(EnumSet.of(Modifier.FINAL)), ConstantDataManager.OTHER, make.Type(type.asType()),
                make.TypeCast(make.Type(type.asType()), make.Identifier(ConstantDataManager.OBJECT))));
        Iterator<String> iterator = nameToTypeMap.keySet().iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String typeName = nameToTypeMap.get(name);
            boolean isLastElement = !iterator.hasNext();
            BinaryTree condition;
            switch (typeName) {
                case ConstantDataManager.CHAR:
                case ConstantDataManager.BYTE:
                case ConstantDataManager.SHORT:
                case ConstantDataManager.INT:
                case ConstantDataManager.LONG:
                    if (isLastElement) {
                        condition =
                                make.Binary(Kind.EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.GET_PREFIX
                                                                + capitalize(name)),
                                                        Collections.emptyList()).toString()));
                        statements.add(make.Return(condition));
                    } else {
                        condition =
                                make.Binary(Kind.NOT_EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.GET_PREFIX
                                                                + capitalize(name)),
                                                        Collections.emptyList()).toString()));
                        statements.add(make.If(condition,
                                make.Return(make.Identifier(ConstantDataManager.FALSE)),
                                null));
                    }
                    break;
                case ConstantDataManager.BOOLEAN:
                    if (isLastElement) {
                        condition =
                                make.Binary(Kind.EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.IS_PREFIX + capitalize(name)),
                                                        Collections.emptyList()).toString()));
                        statements.add(make.Return(condition));
                    } else {
                        condition =
                                make.Binary(Kind.NOT_EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.IS_PREFIX + capitalize(name)),
                                                        Collections.emptyList()).toString()));
                        statements.add(make.If(condition,
                                make.Return(make.Identifier(ConstantDataManager.FALSE)),
                                null));
                    }
                    break;
                case ConstantDataManager.FLOAT:
                    if (isLastElement) {
                        condition =
                                make.Binary(Kind.EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_FLOAT_TYPE),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.FLOAT_TO_INT_BITS),
                                                        Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                make.MethodInvocation(Collections.emptyList(),
                                                                        make
                                                                                .Identifier(ConstantDataManager.GET_PREFIX
                                                                                        + capitalize(
                                                                                                name)),
                                                                        Collections.emptyList()).toString())))
                                                        .toString()));
                        statements.add(make.Return(condition));
                    } else {
                        condition =
                                make.Binary(Kind.NOT_EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_FLOAT_TYPE),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.FLOAT_TO_INT_BITS),
                                                        Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                make.MethodInvocation(Collections.emptyList(),
                                                                        make
                                                                                .Identifier(ConstantDataManager.GET_PREFIX
                                                                                        + capitalize(
                                                                                                name)),
                                                                        Collections.emptyList()).toString())))
                                                        .toString()));
                        statements.add(make.If(condition,
                                make.Return(make.Identifier(ConstantDataManager.FALSE)),
                                null));
                    }
                    break;
                case ConstantDataManager.DOUBLE:
                    if (isLastElement) {
                        condition =
                                make.Binary(Kind.EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_DOUBLE_TYPE),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.DOUBLE_TO_LONG_BITS),
                                                        Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                make.MethodInvocation(Collections.emptyList(),
                                                                        make
                                                                                .Identifier(ConstantDataManager.GET_PREFIX
                                                                                        + capitalize(
                                                                                                name)),
                                                                        Collections.emptyList()).toString())))
                                                        .toString()));
                        statements.add(make.Return(condition));
                    } else {
                        condition =
                                make.Binary(Kind.NOT_EQUAL_TO,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                name),
                                        make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_DOUBLE_TYPE),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.DOUBLE_TO_LONG_BITS),
                                                        Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                make.MethodInvocation(Collections.emptyList(),
                                                                        make
                                                                                .Identifier(ConstantDataManager.GET_PREFIX
                                                                                        + capitalize(
                                                                                                name)),
                                                                        Collections.emptyList()).toString())))
                                                        .toString()));
                        statements.add(make.If(condition, make.Return(make.Identifier(ConstantDataManager.FALSE)), null));
                    }
                    break;
                default:
                    String getterPrefix = ConstantDataManager.GET_PREFIX;
                    if (typeName.equals(ConstantDataManager.BOOLEAN_TYPE)
                            || typeName.equals(ConstantDataManager.SIMPLE_WRAPPER_BOOLEAN_TYPE)) {
                        getterPrefix = ConstantDataManager.IS_PREFIX;
                    }
                    if (isLastElement) {
                        MemberSelectTree returnCondition =
                                make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_OBJECTS_TYPE_NAME),
                                        make.MethodInvocation(Collections.emptyList(),
                                                make.Identifier(ConstantDataManager.EQUALS_METHOD_NAME),
                                                Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                        name),
                                                        make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                make.MethodInvocation(
                                                                        Collections.emptyList(),
                                                                        make
                                                                                .Identifier(getterPrefix
                                                                                        + capitalize(name)),
                                                                        Collections.emptyList()).toString())))
                                                .toString());
                        statements.add(make.Return(returnCondition));
                    } else {
                        UnaryTree cond =
                                make.Unary(Kind.LOGICAL_COMPLEMENT,
                                        make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_OBJECTS_TYPE_NAME),
                                                make.MethodInvocation(Collections.emptyList(),
                                                        make.Identifier(ConstantDataManager.EQUALS_METHOD_NAME),
                                                        Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                                name),
                                                                make.MemberSelect(make.Identifier(ConstantDataManager.OTHER),
                                                                        make.MethodInvocation(
                                                                                Collections.emptyList(),
                                                                                make
                                                                                        .Identifier(getterPrefix
                                                                                                + capitalize(name)),
                                                                                Collections.emptyList()).toString())))
                                                        .toString()));
                        statements.add(make.If(cond, make.Return(make.Identifier(ConstantDataManager.FALSE)), null));
                    }
            }
        }
        BlockTree body = make.Block(statements, false);
        List<AnnotationTree> annotations = new LinkedList<>();
        TypeElement override = workingCopy.getElements().getTypeElement(ConstantDataManager.OVERRIDE_TYPE);
        if (override
                != null) {
            annotations.add(workingCopy.getTreeMaker().Annotation(workingCopy.getTreeMaker().QualIdent(override),
                    Collections
                            .<ExpressionTree>emptyList()));
        }
        ModifiersTree modifiersTree = make.Modifiers(modifiers, annotations);
        MethodTree equalsMethod =
                make.Method(modifiersTree, ConstantDataManager.EQUALS_METHOD_NAME,
                        make.PrimitiveType(TypeKind.BOOLEAN),
                        Collections.<TypeParameterTree>emptyList(),
                        params,
                        Collections.<ExpressionTree>emptyList(),
                        body,
                        null);
        return make.addClassMember(newClassTree, equalsMethod);
    }

    private ClassTree addHashCodeMethodToClass(WorkingCopy workingCopy, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        int startNumber = generatePrimeNumber(2, 10);
        int multiplyNumber = generatePrimeNumber(10, 100);
        List<StatementTree> statements = new ArrayList<>();
        //int hash = <startNumber>;
        statements.add(make.Variable(make.Modifiers(EnumSet.noneOf(Modifier.class
        )), ConstantDataManager.HASH, make.PrimitiveType(
                TypeKind.INT), make.Literal(startNumber)));
        ExpressionTree variableRead;
        for (Entry<String, String> entry : nameToTypeMap.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            switch (fieldType) {
                case ConstantDataManager.BYTE:
                case ConstantDataManager.SHORT:
                case ConstantDataManager.INT:
                case ConstantDataManager.CHAR:
                case ConstantDataManager.SIMPLE_WRAPPER_BYTE_TYPE:
                case ConstantDataManager.SIMPLE_WRAPPER_SHORT_TYPE:
                case ConstantDataManager.SIMPLE_WRAPPER_INTEGER_TYPE:
                case ConstantDataManager.SIMPLE_WRAPPER_CHARACTER_TYPE:
                    variableRead =
                            make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                    fieldName);
                    break;
                case ConstantDataManager.LONG:
                case ConstantDataManager.SIMPLE_WRAPPER_LONG_TYPE:
                    variableRead =
                            make.TypeCast(make.PrimitiveType(TypeKind.INT),
                                    make.Parenthesized(make.Binary(Kind.XOR,
                                            make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                    fieldName),
                                            make.Parenthesized(make.Binary(Kind.UNSIGNED_RIGHT_SHIFT,
                                                    make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                            fieldName),
                                                    make.Literal(32))))));
                    break;
                case ConstantDataManager.FLOAT:
                case ConstantDataManager.SIMPLE_WRAPPER_FLOAT_TYPE:
                    variableRead =
                            make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_FLOAT_TYPE),
                                    make.MethodInvocation(Collections.emptyList(),
                                            make.Identifier(ConstantDataManager.FLOAT_TO_INT_BITS),
                                            Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                    fieldName))).toString());
                    break;
                case ConstantDataManager.DOUBLE:
                case ConstantDataManager.SIMPLE_WRAPPER_DOUBLE_TYPE:
                    variableRead =
                            make.TypeCast(make.PrimitiveType(TypeKind.INT),
                                    make.Parenthesized(make.Binary(Kind.XOR,
                                            make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_DOUBLE_TYPE),
                                                    make.MethodInvocation(Collections.emptyList(),
                                                            make.Identifier(ConstantDataManager.DOUBLE_TO_LONG_BITS),
                                                            Arrays.asList(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                                                    fieldName))).toString()),
                                            make.Parenthesized(make.Binary(Kind.UNSIGNED_RIGHT_SHIFT,
                                                    make.MemberSelect(make.Identifier(ConstantDataManager.SIMPLE_WRAPPER_DOUBLE_TYPE),
                                                            make.MethodInvocation(Collections.emptyList(),
                                                                    make.Identifier(ConstantDataManager.DOUBLE_TO_LONG_BITS),
                                                                    Arrays.asList(make
                                                                            .MemberSelect(make
                                                                                    .Identifier(ConstantDataManager.THIS),
                                                                                    fieldName)))
                                                                    .toString()),
                                                    make.Literal(32))))));
                    break;
                case ConstantDataManager.BOOLEAN:
                case ConstantDataManager.SIMPLE_WRAPPER_BOOLEAN_TYPE:
                    variableRead =
                            make.Parenthesized(make.ConditionalExpression(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                    fieldName),
                                    make.Literal(1),
                                    make.Literal(0)
                            ));
                    break;
                default:
                    variableRead =
                            make.Parenthesized(make.ConditionalExpression(make.Binary(Kind.NOT_EQUAL_TO,
                                    make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                            fieldName),
                                    make.Literal(null)),
                                    make.MemberSelect(make.MemberSelect(make.Identifier(ConstantDataManager.THIS),
                                            fieldName),
                                            make.MethodInvocation(Collections.emptyList(),
                                                    make.Identifier(ConstantDataManager.HASH_CODE_METHOD_NAME),
                                                    Collections.emptyList()).toString()),
                                    make.Literal(0)));
            }
            statements.add(make.ExpressionStatement(make.Assignment(make.Identifier(ConstantDataManager.HASH),
                    make.Binary(Tree.Kind.PLUS,
                            make.Binary(Tree.Kind.MULTIPLY,
                                    make.Literal(multiplyNumber),
                                    make.Identifier(ConstantDataManager.HASH)),
                            variableRead))));
        }
        statements.add(make.Return(make.Identifier(ConstantDataManager.HASH)));
        BlockTree body = make.Block(statements, false);
        List<AnnotationTree> annotations = new LinkedList<>();
        TypeElement override = workingCopy.getElements().getTypeElement(ConstantDataManager.OVERRIDE_TYPE);
        if (override != null) {
            annotations.add(
                    make.Annotation(
                            make.QualIdent(override),
                            Collections.<ExpressionTree>emptyList()));
        }
        ModifiersTree modifiersTree =
                make.Modifiers(
                        EnumSet.of(Modifier.PUBLIC),
                        annotations);
        MethodTree hashCodeMethodTree =
                make.Method(modifiersTree, ConstantDataManager.HASH_CODE_METHOD_NAME,
                        make.PrimitiveType(TypeKind.INT),
                        Collections.<TypeParameterTree>emptyList(),
                        Collections.<VariableTree>emptyList(),
                        Collections.<ExpressionTree>emptyList(),
                        body,
                        null);
        return make.addClassMember(newClassTree, hashCodeMethodTree);
    }

    private ClassTree addToStringMethodToClass(WorkingCopy workingCopy, TypeElement type, ClassTree classTree) {
        ClassTree newClassTree = classTree;
        TreeMaker make = workingCopy.getTreeMaker();
        List<AnnotationTree> annotations = new LinkedList<>();
        TypeElement override = workingCopy.getElements().getTypeElement(ConstantDataManager.OVERRIDE_TYPE);
        if (override != null) {
            annotations.add(
                    make.Annotation(
                            make.QualIdent(override),
                            Collections.<ExpressionTree>emptyList()));
        }
        ModifiersTree modifiersTree =
                make.Modifiers(
                        EnumSet.of(Modifier.PUBLIC),
                        annotations);
        ExpressionTree exp = make.Literal(type.getSimpleName().toString() + '{');
        boolean first = true;
        for (Map.Entry<String, String> entry : nameToTypeMap.entrySet()) {
            StringBuilder sb = new StringBuilder();
            if (!first) {
                sb.append(", ");
            }
            String fieldName = entry.getKey();
            sb.append(fieldName).append('=');
            exp = make.Binary(Tree.Kind.PLUS, exp, make.Literal(sb.toString()));
            exp = make.Binary(Tree.Kind.PLUS, exp, make.Identifier(fieldName));
            first = false;
        }
        StatementTree returnStatement = make.Return(make.Binary(Tree.Kind.PLUS, exp, make.Literal('}')));
        MethodTree toStringMethodTree =
                make.Method(modifiersTree, ConstantDataManager.TO_STRING_METHOD_NAME,
                        make.Type(ConstantDataManager.STRING_TYPE),
                        Collections.<TypeParameterTree>emptyList(),
                        Collections.<VariableTree>emptyList(),
                        Collections.<ExpressionTree>emptyList(),
                        make.Block(Arrays.asList(returnStatement), false),
                        null);
        return make.addClassMember(newClassTree, toStringMethodTree);
    }

    private int generatePrimeNumber(int lowerLimit, int higherLimit) {
        if (ConstantDataManager.RANDOM_NUMBER > 0) {
            return ConstantDataManager.RANDOM_NUMBER;
        }
        Random r = new Random(System.currentTimeMillis());
        int proposed = r.nextInt(higherLimit - lowerLimit) + lowerLimit;
        while (!isPrimeNumber(proposed)) {
            proposed++;
        }
        if (proposed > higherLimit) {
            proposed--;
            while (!isPrimeNumber(proposed)) {
                proposed--;
            }
        }
        return proposed;
    }

    private boolean isPrimeNumber(int n) {
        int squareRoot = (int) Math.sqrt(n) + 1;
        if (n % 2 == 0) {
            return false;
        }
        for (int cntr = 3; cntr < squareRoot; cntr++) {
            if (n % cntr == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void initialize(WizardDescriptor wizard) {
        this.wizard = wizard;
    }

    @Override public void uninitialize(WizardDescriptor wizard) {
        panels = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return getPanels().get(index);
    }

    @Override public String name() {
        return index + 1 + ". from " + getPanels().size();
    }

    @Override
    public boolean hasNext() {
        return index < getPanels().size() - 1;
    }

    @Override public boolean hasPrevious() {
        return index > 0;
    }

    @Override public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    @Override public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
    // If something changes dynamically (besides moving between panels), e.g.
    // the number of panels changes in response to user input, then use
    // ChangeSupport to implement add/removeChangeListener and call fireChange
    // when needed

    // You could safely ignore this method. Is is here to keep steps which were
    // there before this wizard was instantiated. It should be better handled
    // by NetBeans Wizard API itself rather than needed to be implemented by a
    // client code.
    private String[] createSteps() {
        String[] beforeSteps = (String[]) wizard.getProperty("WizardPanel_contentData");
        assert beforeSteps != null : "This wizard may only be used embedded in the template wizard";
        String[] res = new String[(beforeSteps.length - 1) + panels.size()];
        for (int i = 0; i < res.length; i++) {
            if (i < (beforeSteps.length - 1)) {
                res[i] = beforeSteps[i];
            } else {
                res[i] = panels.get(i - beforeSteps.length + 1).getComponent().getName();
            }
        }
        return res;
    }
}
