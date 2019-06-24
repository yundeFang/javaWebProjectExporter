package com.hld.exporter;

import com.hld.exporter.module.ExportFileModule;
import com.hld.exporter.util.ExporterUtil;
import com.intellij.javaee.web.WebUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import javax.tools.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ExporterDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JList selectedFilesJList;
    private JScrollPane filesScrollPanel;
    private JPanel panelCenter;
    private TextFieldWithBrowseButton fileChooserText;
    private JPanel toolPanel;
    private JComboBox compileLevelCombo;

    private AnActionEvent event;
    private DefaultListModel selectedFiles = new DefaultListModel();


    private Map<String, ExportFileModule> pathMap = new HashMap<>();

    public ExporterDialog(AnActionEvent event) {
        this.event = event;
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        fileChooserText.addActionListener(e -> {
            //文件选择的装饰器
            FileChooserDescriptor des = new FileChooserDescriptor(false, true, false, false, false, false);
            des.setTitle("Export Path Chooser");
            //路径选择工厂类
            FileChooserFactoryImpl pathChooserFactory = new FileChooserFactoryImpl();
            //路径选择
            FileChooserDialogImpl pathChooser = (FileChooserDialogImpl) pathChooserFactory.createPathChooser(des, event.getProject(), contentPane);

            Ref<VirtualFile> fileRef = Ref.create();
            pathChooser.setResizable(false);
            pathChooser.choose(null, files -> fileRef.set(files.get(0)));
            if (!fileRef.isNull()) {
                //创建工具条
                File file = VfsUtilCore.virtualToIoFile(fileRef.get());
                fileChooserText.setText(file.getAbsolutePath());
            }
        });

    }

    private void onOK() {


        // The Export Path is required.
        if (null == fileChooserText.getText() || "".equals(fileChooserText.getText())) {
            Messages.showErrorDialog("Export Path is Required", "Warning Message");
            fileChooserText.grabFocus();
            return;
        }
//
//        // File Name is required.
//        if (null == inputFileName.getText() || "".equals(inputFileName.getText())) {
//            Messages.showErrorDialog("File Name is Required", "Warning Message");
//            inputFileName.grabFocus();
//            return;
//        }
        // 导出路径
        String exportPath = fileChooserText.getText();
        // 如果没有则创建
        ExporterUtil.mkdirs(exportPath);

        //待导出的文件路径们
        Object[] fileStrs = selectedFiles.toArray();

        // 循环所有存在的路径
        // 1.获取module路径 为各个子目录
        // 1.1每个module 创建
        // 2.判断是java 文件 加入数组
        // 2.1 编译至 \模块\WEB-INF\classes
        // 3.判断其他文件 如果是resource 复制到 \WEB-INF\classes
        // 4.如果是webroot 文件 则 复制到 \模块\下

        //按照模块分类
        Map<Module, List<VirtualFile>> fileClassifiedByModule = new HashMap<>();
        for (Object filePath : fileStrs) {
            VirtualFile file = pathMap.get(filePath).getFile();
            if (file.isDirectory()) continue;
            Module fileModule = ModuleUtil.findModuleForFile(file, event.getProject());
            List<VirtualFile> list = fileClassifiedByModule.get(fileModule);
            if (null == list) {
                list = new ArrayList<>();
                fileClassifiedByModule.put(fileModule, list);
            }
            list.add(file);
        }

        // User needs to choose the compilation level of Java.
        // Default compilation level is 1.7.
//        if (compileCheckBox.isSelected()) {
        // source目录
        List<VirtualFile> sourceRoots;
        // resource目录
        List<VirtualFile> resourceRoots;
        // 各个模块创建目录
        String moduleExportPath;
        // 各个模块字节码文件目录
        String moduleExportClassPath;
        // 待编译的java文件
        List<String> javaFilesPaths;

        // compile sources
        String modulePath;


        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        for (Module module : fileClassifiedByModule.keySet()) {
            //source目录
            sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE);
            //resource目录
            resourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE);
            //各个模块创建目录
            moduleExportPath = exportPath + File.separator + module.getName();
            ExporterUtil.mkdirs(moduleExportPath);

            moduleExportClassPath = moduleExportPath + File.separator + "WEB-INF\\classes";
            ExporterUtil.mkdirs(moduleExportClassPath);

            //List<WebRoot> webRoots = ExporterUtil.getWebRootUrl(module);
            //待编译的文件
            javaFilesPaths = new ArrayList<>();

            modulePath = ModuleUtil.getModuleDirPath(module);

            for (VirtualFile exportFile : fileClassifiedByModule.get(module)) {
                //如果是java那么添加到待编译的目录
                if (ExporterUtil.isJavaFile(exportFile)) {
                    javaFilesPaths.add(exportFile.getPath());
                    continue;
                }
                try {
                    // 如果是来自于source目录的非java文件
                    VirtualFile sourceFile = ExporterUtil.getSource4File(sourceRoots, resourceRoots, exportFile, event.getProject());
                    if (null != sourceFile) {
                        String filePath = moduleExportClassPath + File.separator + exportFile.getPath().replace(sourceFile.getPath(), "");
                        FileUtils.copyFile(new File(exportFile.getPath()), new File(filePath));
                        continue;
                    }
                } catch (IOException e) {
                }

//                    String webRoot = ExporterUtil.getWebRoot4File(webRoots, exportFile);

                String webPath = WebUtil.getWebUtil().getWebPath(event.getProject(), exportFile);

                // If file belongs to a webRoot, we will copy this file to the relative path in ExportPath.
                if (null != webPath && !"".equals(webPath)) {
                    String filePath = moduleExportPath + webPath;
                    try {
                        FileUtils.copyFile(new File(exportFile.getPath()), new File(filePath));
                    } catch (IOException e) {
                    }
                }
            }
            if (javaFilesPaths.size() > 0) {
                //classpath
                String jars = ExporterUtil.getJars(module);
                //编译配置
                String compileLevel = compileLevelCombo.getSelectedItem().toString();
                Iterable<String> options = Arrays.asList("-sourcepath", modulePath,
                        "-classpath", jars,
                        "-d", moduleExportClassPath,
                        "-encoding", "utf-8",
                        "-source", compileLevel,
                        "-target", compileLevel);

                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(javaFilesPaths);
                JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
                task.call();

                for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                    // System.out.format("%s[line %d column %d]-->%s%n", diagnostic.getKind(), diagnostic.getLineNumber(),
                    // diagnostic.getColumnNumber(),
                    // diagnostic.getMessage(null));
                    System.out.println(diagnostic.getMessage(null));
                }
                try {
                    fileManager.close();
                } catch (IOException e) {
                }
            }
        }
        // If the compileCheckBox isn't checked then we will copy the selected files to the user-specified directory.
        // The user-specified directory consists of Export Path and File Name.
//        } else {
//
//
//        }

        try {
            Desktop.getDesktop().open(new File(exportPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    public static void main(String[] args) {
        ExporterDialog dialog = new ExporterDialog(null);
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    private void createUIComponents() {
        selectedFilesJList = new JBList();
        VirtualFile[] datas = event.getData(DataKeys.VIRTUAL_FILE_ARRAY);

        // 导出文件队列
        Queue<VirtualFile> datasQueue = new LinkedBlockingQueue<>();
        datasQueue.addAll(Arrays.asList(datas));

        // 导出文件队列中添加子文件
        List<VirtualFile> files = ExporterUtil.getAllFiles(Arrays.asList(datas));

        for (VirtualFile file : files) {
            //文件所属模块
            Module module = ModuleUtil.findModuleForFile(file, event.getProject());
            //模块路径
            String modulePath = ModuleUtil.getModuleDirPath(module);
            //展示文件路径
            String filePath = "/" + module.getName() + file.getPath().replace(modulePath, "");
            //全部存一下
            pathMap.put(filePath, new ExportFileModule(filePath, file, module));
            //展示文件列表
            selectedFiles.addElement(filePath);
        }

        selectedFilesJList.setModel(selectedFiles);
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(selectedFilesJList);
        addToolbarButtons(toolbarDecorator);

        // 默认选中第一个
        if (selectedFiles.getSize() > 0) selectedFilesJList.setSelectedIndex(0);
        // 路径选择框
        fileChooserText = new TextFieldWithBrowseButton();
        fileChooserText.setTextFieldPreferredWidth(50);

        // 工具条面板
        toolPanel = toolbarDecorator.createPanel();
        toolPanel.setBorder(null);

        // 编译级别的选择
        compileLevelCombo = new ComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("1.8");
        defaultComboBoxModel1.addElement("1.7");
        defaultComboBoxModel1.addElement("1.6");
        compileLevelCombo.setModel(defaultComboBoxModel1);
        compileLevelCombo.setSelectedIndex(1);

    }

    protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("CompileLevel");
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(compileLevelCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelCenter = new JPanel();
        panelCenter.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panelCenter, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        filesScrollPanel = new JScrollPane();
        panelCenter.add(filesScrollPanel, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        selectedFilesJList.setEnabled(true);
        selectedFilesJList.setLayoutOrientation(0);
        selectedFilesJList.setSelectionMode(0);
        filesScrollPanel.setViewportView(selectedFilesJList);
        panelCenter.add(toolPanel, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Export Path");
        contentPane.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileChooserText.setEditable(false);
        contentPane.add(fileChooserText, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
