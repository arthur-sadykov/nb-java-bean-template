/*
 * Copyright (c) 2020 Arthur Sadykov.
 */
package nb.java.bean.template;

import java.util.HashSet;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

/**
 *
 * @author Arthur Sadykov
 */
public class JavaBeanWizardPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    /**
     * The visual component that displays this panel. If you need to access the component from this class, just use
     * getComponent().
     */
    private JavaBeanVisualPanel component;
    private WizardDescriptor wizardDescriptor;
    private final Set<ChangeListener> listeners = new HashSet<>(1);

    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizardard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override public JavaBeanVisualPanel getComponent() {
        if (component == null) {
            component = new JavaBeanVisualPanel(this);
        }
        return component;
    }

    @Override public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx("help.key.here");
    }

    @Override public boolean isValid() {
        getComponent();
        return component.valid(wizardDescriptor);
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    protected void fireChangeEvent() {
        Set<ChangeListener> ls;
        synchronized (listeners) {
            ls = new HashSet<>(listeners);
        }
        ChangeEvent ev = new ChangeEvent(this);
        ls.forEach(listener -> {
            listener.stateChanged(ev);
        });
    }

    @Override public void readSettings(WizardDescriptor wizardDescriptor) {
        this.wizardDescriptor = wizardDescriptor;
        component.read(wizardDescriptor);
    }

    @Override public void storeSettings(WizardDescriptor wizardDescriptor) {
        component.store(wizardDescriptor);
    }
}
