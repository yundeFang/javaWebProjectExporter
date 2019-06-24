package com.hld.exporter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.awt.*;

public class ExportMainAction extends AnAction {

    public void actionPerformed(AnActionEvent e) {
        ExporterDialog dialog = new ExporterDialog(e);
        dialog.setSize(500, 500);
        dialog.setTitle("Export Selections");
        Toolkit tk = Toolkit.getDefaultToolkit();
        dialog.setLocation((tk.getScreenSize().width - 500)/2,(tk.getScreenSize().height - 500)/2);
        dialog.setVisible(true);
    }

}
