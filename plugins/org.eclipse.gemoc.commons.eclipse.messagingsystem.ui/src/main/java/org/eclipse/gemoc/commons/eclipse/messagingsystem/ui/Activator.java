/*******************************************************************************
 * Copyright (c) 2011, 2017 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Inria - initial API and implementation
 *******************************************************************************/
package org.eclipse.gemoc.commons.eclipse.messagingsystem.ui;

import java.io.PrintStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.helper.TeeOutputStream;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.internal.EclipseConsoleOutputStream;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.internal.console.ConsoleIO;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.internal.console.EclipseConsoleIO;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.internal.console.EclipseConsoleIOFactory;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.ui.preferences.PreferenceConstants;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.gemoc.commons.eclipse.messagingsystem.ui"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	// protected MessagingSystem messaggingSystem;
	protected EclipseConsoleIO consoleIO = null;
	
	protected static boolean isPluginFullyInitialized = false;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
		this.getPreferenceStore(); // get preference store on start in order to avoid entering in the synchronized section later
		Boolean mustCapture = getPreferenceStore()
				.getBoolean(PreferenceConstants.P_CAPTURE_SYSTEM_ERROUT);
		if(mustCapture) {
			Job j = new Job("Waiting workbench to redirect system.out to console") {
	
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					final boolean[] workbenchAvailable = new boolean[] { false };
					Display.getDefault().syncExec(new Runnable() {
	
						@Override
						public void run() {
							if (PlatformUI.isWorkbenchRunning() && PlatformUI.getWorkbench() != null
									&& PlatformUI.getWorkbench().getWorkbenchWindows().length > 0 && PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null) {
								workbenchAvailable[0] = true;
							}
						}
					});
					
					if (workbenchAvailable[0]) {
						Boolean mustCapture = getPreferenceStore()
								.getBoolean(PreferenceConstants.P_CAPTURE_SYSTEM_ERROUT);
						if(mustCapture) {
							Display.getDefault().syncExec(new Runnable() {
		
								@Override
								public void run() {
									captureSystemOutAndErr();
									isPluginFullyInitialized = true;
								}
							});
						} else {
							isPluginFullyInitialized = true;
						}
					} else {
						System.out.println("Waiting for the Workbench ...");
						schedule(1000);
					}
					return Status.OK_STATUS;
				}
			};
	
			j.schedule(200);
		}
		else {
			isPluginFullyInitialized = true;
		}
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext
	 * )
	 */
	public void stop(BundleContext context) throws Exception {

		releaseSystemOutAndErr();
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}



	public void clearConsole() {
		getConsoleIO().clear();
	}

	synchronized public ConsoleIO getConsoleIO() {
		// makes sure that external call to getConsoleIO() occurs only when the plugin is fully operational
		// ie. workbench is started and system.out is possibly redirected 
		while (!isPluginFullyInitialized) {
			try {
				System.out.println("waiting messagingsystem.ui.Activator.isPluginFullyInitialized");
				Thread.sleep(500);
			} catch (InterruptedException e) {}
		}
		return getConsoleIOInternal();
	}
	private ConsoleIO getConsoleIOInternal() {
		if (consoleIO == null) {
			
			String bundleSymbolicName = getBundle().getHeaders().get("Bundle-SymbolicName").toString();
			String consoleUId = bundleSymbolicName + this.hashCode();
			consoleIO = EclipseConsoleIOFactory.getInstance().getConsoleIO(consoleUId,
					"Default MessagingSystem console");
		}
		return consoleIO;
	}

	protected PrintStream originalSystemOut = null;
	protected PrintStream originalSystemErr = null;

	/**
	 * set the current System.out and System.err so they are redirected to our
	 * default console
	 */
	public void captureSystemOutAndErr() {

		if (originalSystemOut != System.out) {
			originalSystemOut = System.out;
			TeeOutputStream teeOutputStream = new TeeOutputStream(originalSystemOut,
					new EclipseConsoleOutputStream(Activator.getDefault().getConsoleIOInternal(), false));
			System.setOut(new PrintStream(teeOutputStream));
		}
		if (originalSystemErr != System.err) {
			originalSystemErr = System.err;
			TeeOutputStream teeOutputStream = new TeeOutputStream(originalSystemErr,
					new EclipseConsoleOutputStream(Activator.getDefault().getConsoleIOInternal(), true));
			System.setErr(new PrintStream(teeOutputStream));
		}
		System.out.println("System.out and System.err copied to Eclipse console");
	}

	/**
	 * set back the System.out and System.err to their original values
	 */
	public void releaseSystemOutAndErr() {
		if (System.out != null)
			System.out.flush();
		if (System.err != null)
			System.err.flush();
		if (consoleIO != null)
			consoleIO.print("Stopping redirection of System.out and System.err to this console.\n");
		if (originalSystemOut != null)
			System.setOut(originalSystemOut);
		if (originalSystemErr != null)
			System.setErr(originalSystemErr);
		originalSystemOut = null;
		originalSystemErr = null;
	}

}