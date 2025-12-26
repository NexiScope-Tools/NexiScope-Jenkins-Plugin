package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.matrix.MatrixRun;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.util.logging.Logger;

/**
 * Listener for individual matrix combination runs (MatrixRun).
 * 
 * This listener detects when individual matrix combinations start/complete
 * and notifies the MatrixBuildListener.
 * 
 * @author NexiScope Team
 */
@Extension
public class MatrixRunListener extends RunListener<MatrixRun> {
    
    private static final Logger LOGGER = Logger.getLogger(MatrixRunListener.class.getName());
    
    public MatrixRunListener() {
        super(MatrixRun.class);
    }
    
    @Override
    public void onStarted(MatrixRun run, TaskListener listener) {
        // Notify MatrixBuildListener
        MatrixBuildListener.onMatrixRunStarted(run);
    }
    
    @Override
    public void onCompleted(MatrixRun run, TaskListener listener) {
        // Notify MatrixBuildListener
        MatrixBuildListener.onMatrixRunCompleted(run);
    }
}

