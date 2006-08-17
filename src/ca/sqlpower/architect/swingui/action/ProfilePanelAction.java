package ca.sqlpower.architect.swingui.action;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.ArchitectException;
import ca.sqlpower.architect.ArchitectRuntimeException;
import ca.sqlpower.architect.ArchitectUtils;
import ca.sqlpower.architect.SQLColumn;
import ca.sqlpower.architect.SQLObject;
import ca.sqlpower.architect.SQLTable;
import ca.sqlpower.architect.profile.ProfileHTMLFormat;
import ca.sqlpower.architect.profile.ProfileManager;
import ca.sqlpower.architect.profile.ProfilePDFFormat;
import ca.sqlpower.architect.swingui.ASUtils;
import ca.sqlpower.architect.swingui.ArchitectFrame;
import ca.sqlpower.architect.swingui.ArchitectPanelBuilder;
import ca.sqlpower.architect.swingui.CommonCloseAction;
import ca.sqlpower.architect.swingui.DBTree;
import ca.sqlpower.architect.swingui.JDefaultButton;
import ca.sqlpower.architect.swingui.ProfilePanel;
import ca.sqlpower.architect.swingui.ProfileTableCellRenderer;
import ca.sqlpower.architect.swingui.ProfileTableModel;
import ca.sqlpower.architect.swingui.ProgressWatcher;
import ca.sqlpower.architect.swingui.SwingUserSettings;
import ca.sqlpower.architect.swingui.ProfilePanel.ChartTypes;

import com.jgoodies.forms.builder.ButtonBarBuilder;

public class ProfilePanelAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(ProfilePanelAction.class);

    protected DBTree dbTree;
    protected ProfileManager profileManager;
    protected JDialog d;
            
    
    public ProfilePanelAction() {
        super("Profile...", ASUtils.createJLFIcon( "general/Information",
                        "Information", 
                        ArchitectFrame.getMainInstance().getSprefs().getInt(SwingUserSettings.ICON_SIZE, 24)));
        
        putValue(SHORT_DESCRIPTION, "Profile Tables");
    }
    
    /**
     * Called to pop up the ProfilePanel
     */
    public void actionPerformed(ActionEvent e) {
        if (dbTree == null) {
            logger.debug("dbtree was null when actionPerformed called");
            return;
        }
        try {
            if ( dbTree.getSelectionPaths() == null )
                return;
            
            final Set <SQLTable> tables = new HashSet();
            for ( TreePath p : dbTree.getSelectionPaths() ) {
                SQLObject so = (SQLObject) p.getLastPathComponent();
                Collection<SQLTable> tablesUnder = ArchitectUtils.tablesUnder(so);
                System.out.println("Tables under "+so+" are: "+tablesUnder);
                tables.addAll(tablesUnder);
            }
            
            profileManager.setCancelled(false);
            d = new JDialog(ArchitectFrame.getMainInstance(), "Table Profiles");
            
            
            Action closeAction = new AbstractAction() {
                public void actionPerformed(ActionEvent evt) {
                    profileManager.setCancelled(true);
                    d.setVisible(false);
                }
            };
            closeAction.putValue(Action.NAME, "Close");
            final JDefaultButton closeButton = new JDefaultButton(closeAction);
            
            final JPanel progressViewPanel = new JPanel(new BorderLayout());
            final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(closeButton);
            progressViewPanel.add(buttonPanel, BorderLayout.SOUTH);
            final JProgressBar progressBar = new JProgressBar();
            progressBar.setPreferredSize(new Dimension(450,20));
            progressViewPanel.add(progressBar, BorderLayout.CENTER);
            final JLabel workingOn = new JLabel("Profiling:");
            progressViewPanel.add(workingOn, BorderLayout.NORTH);

            ArchitectPanelBuilder.makeJDialogCancellable(
                    d, new CommonCloseAction(d));
            d.getRootPane().setDefaultButton(closeButton);
            d.setContentPane(progressViewPanel);
            d.pack();
            d.setLocationRelativeTo(ArchitectFrame.getMainInstance());
            d.setVisible(true);

            new ProgressWatcher(progressBar,profileManager,workingOn);

            new Thread( new Runnable() {

                public void run() {
                    try {
                        List<SQLTable> toBeProfiled = new ArrayList<SQLTable>();
                        for (SQLTable t: tables) {
                            if (profileManager.getResult(t)== null) {
                                toBeProfiled.add(t);
                                workingOn.setText("Adding "+t.getName()+
                                        "  ("+toBeProfiled.size()+")");
                            }
                        }
                        profileManager.createProfiles(toBeProfiled, workingOn);

                        progressBar.setVisible(false);
                        
                        JLabel status = new JLabel("Generating reports, Please wait......");
                        progressViewPanel.add(status, BorderLayout.NORTH);
                        status.setVisible(true);

                        JTabbedPane tabPane = new JTabbedPane();
                       

                        ProfileTableModel tm = new ProfileTableModel();
                        tm.setProfileManager(profileManager);
                        final JTable viewTable = new JTable(tm);
                        ProfilePanelMouseListener profilePanelMouseListener = new ProfilePanelMouseListener();
                        profilePanelMouseListener.setTabPane(tabPane);
                        viewTable.addMouseListener( profilePanelMouseListener);
                        viewTable.setDefaultRenderer(Object.class,new ProfileTableCellRenderer());
                        JScrollPane editorScrollPane = new JScrollPane(viewTable);
                        editorScrollPane.setVerticalScrollBarPolicy(
                                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                        editorScrollPane.setPreferredSize(new Dimension(800, 600));
                        editorScrollPane.setMinimumSize(new Dimension(10, 10));
                        
                        JPanel tableViewPane = new JPanel(new BorderLayout());

                        tableViewPane.add(editorScrollPane,BorderLayout.CENTER);
                        ButtonBarBuilder buttonBuilder = new ButtonBarBuilder();
                        JButton save = new JButton(new AbstractAction("Save") {
                        
                            public void actionPerformed(ActionEvent e) {

                                JFileChooser chooser = new JFileChooser();
                                
                                chooser.addChoosableFileFilter(ASUtils.PDF_FILE_FILTER);
                                chooser.addChoosableFileFilter(ASUtils.HTML_FILE_FILTER);
                                chooser.removeChoosableFileFilter(chooser.getAcceptAllFileFilter());
                                int response = chooser.showSaveDialog(d);
                                if (response != JFileChooser.APPROVE_OPTION) {
                                    return;
                                } else {
                                    File file = chooser.getSelectedFile();
                                    final FileFilter fileFilter = chooser.getFileFilter();
                                    if (fileFilter == ASUtils.HTML_FILE_FILTER) {
                                        if (!file.getPath().endsWith(".html")) {
                                            file = new File(file.getPath()+".html");
                                        }
                                    } else {
                                        if (!file.getPath().endsWith(".pdf")) {
                                            file = new File(file.getPath()+".pdf");
                                        }
                                    }
                                    if (file.exists()) {
                                        response = JOptionPane.showConfirmDialog(
                                                d,
                                                "The file\n\n"+file.getPath()+"\n\nalready exists. Do you want to overwrite it?",
                                                "File Exists", JOptionPane.YES_NO_OPTION);
                                        if (response == JOptionPane.NO_OPTION) {
                                            actionPerformed(e);
                                            return;
                                        }
                                    }
                                    
                                    final File file2 = new File(file.getPath());
                                    Runnable saveTask = new Runnable() {
                                        public void run() {
                                            List tabList = new ArrayList(tables);
                                            OutputStream out = null;
                                            try {
                                                out = new BufferedOutputStream(new FileOutputStream(file2));
                                                if (fileFilter == ASUtils.HTML_FILE_FILTER){
                                                    final String encoding = "utf-8";
                                                    ProfileHTMLFormat prf = new ProfileHTMLFormat(encoding);
                                                    OutputStreamWriter osw = new OutputStreamWriter(out, encoding);
                                                    osw.append(prf.format(tabList,profileManager));
                                                    osw.flush();
                                                } else {
                                                    new ProfilePDFFormat().createPdf(out, tabList, profileManager);
                                                }
                                            } catch (Exception ex) {
                                                ASUtils.showExceptionDialog(d,"Could not save PDF File", ex);
                                            } finally {
                                                if ( out != null ) {
                                                    try {
                                                        out.flush();
                                                        out.close();
                                                    } catch (IOException ex) {
                                                        ASUtils.showExceptionDialog(d,"Could not close PDF File", ex);
                                                    }
                                                }
                                            }
                                        }
                                    };
                                    new Thread(saveTask).start();
                                }
                            }
                        
                        });

                        JButton refresh = new JButton(new AbstractAction("Refresh"){

                            public void actionPerformed(ActionEvent e) {
                               Set<SQLTable> uniqueTables = new HashSet(); 
                               for (int i: viewTable.getSelectedRows()) {
                                   Object o = viewTable.getValueAt(i,3);
                                   System.out.println(o.getClass());
                                   SQLTable table = (SQLTable) o ;
                                   uniqueTables.add(table);
                               }
                               
                               try {
                                   profileManager.setCancelled(false);
                                   profileManager.createProfiles(uniqueTables);
                                } catch (SQLException e1) {
                                    throw new RuntimeException(e1);
                                } catch (ArchitectException e1) {
                                    throw new ArchitectRuntimeException(e1);
                                }
                                ((ProfileTableModel)viewTable.getModel()).refresh();
                            }
                            
                        });
                        
                        JButton delete  = new JButton(new AbstractAction("Delete"){

                            public void actionPerformed(ActionEvent e) {
                                int[] killMe = viewTable.getSelectedRows();
                                Arrays.sort(killMe);

                                // iterate backwards so the rows don't shift away on us!
                                for (int i = killMe.length-1; i >= 0; i--) {
                                    logger.debug("Deleting row "+killMe[i]+": "+viewTable.getValueAt(killMe[i],4));
                                    SQLColumn col = (SQLColumn) viewTable.getValueAt(killMe[i], 4);
                                    try {
                                        profileManager.remove(col);
                                    } catch (ArchitectException e1) {
                                        ASUtils.showExceptionDialog(d,"Could delete column:", e1);
                                    }
                                }
                                ((ProfileTableModel)viewTable.getModel()).refresh();
                            }
                            
                        });
                        JButton deleteAll = new JButton(new AbstractAction("Delete All"){

                            public void actionPerformed(ActionEvent e) {
                                profileManager.clear();
                                ((ProfileTableModel)viewTable.getModel()).refresh();
                            }
                            
                        });
                        JButton[] buttonArray = {refresh,delete,deleteAll,save,closeButton};
                        buttonBuilder.addGriddedButtons(buttonArray);
                        tableViewPane.add(buttonBuilder.getPanel(),BorderLayout.SOUTH);
                        tabPane.addTab("Table View", tableViewPane );
                        ProfilePanel p = new ProfilePanel(profileManager);
                        tabPane.addTab("Graph View",p);

                        profilePanelMouseListener.setProfilePanel(p);
                        List<SQLTable> list = new ArrayList(tables);
                        p.setTables(list);
                        p.setChartType(ChartTypes.PIE);

                        d.remove(progressViewPanel);
                        d.setContentPane(tabPane);
                        d.pack();
                        d.setLocationRelativeTo(ArchitectFrame.getMainInstance());
                        

                    } catch (SQLException e) {
                        logger.error("Error in Profile Action ", e);
                        ASUtils.showExceptionDialogNoReport(dbTree, "Error during profile run", e);
                    } catch (ArchitectException e) {
                        logger.error("Error in Profile Action", e);
                        ASUtils.showExceptionDialog(dbTree, "Error during profile run", e);
                    }
                }

            }).start();

            
   


            
    
        } catch (Exception ex) {
            logger.error("Error in Profile Action ", ex);
            ASUtils.showExceptionDialog(dbTree, "Error during profile run", ex);
        }
    }


    

    public void setDBTree(DBTree dbTree) {
        this.dbTree = dbTree;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public void setProfileManager(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }
  
    
    /**
     * The PPMouseListener class receives all mouse and mouse motion
     * events in the PlayPen.  It tries to dispatch them to the
     * ppcomponents, and also handles playpen-specific behaviour like
     * rubber band selection and popup menu triggering.
     */
    protected class ProfilePanelMouseListener implements MouseListener  {
        private ProfilePanel profilePanel;
        private JTabbedPane tabPane;

        public ProfilePanel getProfilePanel() {
            return profilePanel;
        }

        public void setProfilePanel(ProfilePanel profilePanel) {
            this.profilePanel = profilePanel;
        }

        public void mouseClicked(MouseEvent evt) {
            // TODO Auto-generated method stub

            Object obj = evt.getSource();
            if (evt.getClickCount() == 2) {
                if ( obj instanceof JTable ) {
                    JTable t = (JTable)obj;
                    SQLColumn col = (SQLColumn)t.getValueAt(t.getSelectedRow(),4);
                    Set<SQLTable> tables = new HashSet<SQLTable>();
                    for (int i =0; i < t.getRowCount(); i++){
                        tables.add((SQLTable)t.getValueAt(i,3));
                    }
                    profilePanel.setTables(new ArrayList(tables));
                    profilePanel.getTableSelector().setSelectedItem(col.getParentTable());
                    profilePanel.getColumnSelector().setSelectedValue(col,true);
                    tabPane.setSelectedIndex(1);
                }
            }
        }

        public void mousePressed(MouseEvent e) {
            // TODO Auto-generated method stub
            
        }

        public void mouseReleased(MouseEvent e) {
            // TODO Auto-generated method stub
            
        }

        public void mouseEntered(MouseEvent e) {
            // TODO Auto-generated method stub
            
        }

        public void mouseExited(MouseEvent e) {
            // TODO Auto-generated method stub
            
        }

        public JTabbedPane getTabPane() {
            return tabPane;
        }

        public void setTabPane(JTabbedPane tabPane) {
            this.tabPane = tabPane;
        }


    
    }

}
