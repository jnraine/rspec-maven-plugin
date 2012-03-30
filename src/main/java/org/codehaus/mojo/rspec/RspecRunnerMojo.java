package org.codehaus.mojo.rspec;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jruby.Ruby;
import org.jruby.RubyBoolean;
import org.jruby.RubyRuntimeAdapter;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Mojo to run Ruby Spec test
 * 
 * @author Michael Ward
 * @author Mauro Talevi
 * @author Diego Carrion
 * @author Andre Goncalves
 * @goal spec
 */
public final class RspecRunnerMojo extends AbstractRspecMojo {
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!canRun()) {
			return;
		}
		final Ruby runtime = ruby();
		final String script = script(runtime);
		boolean result = run(runtime, script);
		processResults(result);
	}

	private boolean canRun() throws MojoExecutionException {
		if (skipTests || skip) {
			getLog().info("Skipping RSpec tests");
			return false;
		}
		validateRubyHome();
		return true;
	}

	private void processResults(boolean result) throws MojoFailureException {
		if (!result) {
			String msg = "RSpec tests failed. See '" + reportFile()
					+ "' for details.";
			getLog().warn(msg);
			getLog().warn(progressString());
			if (!ignoreFailure) {
				throw new MojoFailureException(msg);
			}
		} else {
			String msg = "RSpec tests successful. See '" + reportFile()
					+ "' for full details.";
			getLog().info(msg);
			getLog().info(progressString());
		}
	}

	private String progressString() {
		try {
			return readFileAsString(progressFile());
		} catch (IOException e) {
			getLog().error("An error occurred while reading progress file");
			getLog().error(e);
			return "";
		}
	}

	private String readFileAsString(String filePath) throws java.io.IOException{
		StringBuffer fileData = new StringBuffer(1000);
		BufferedReader reader = new BufferedReader(
				new FileReader(filePath));
		char[] buf = new char[1024];
		int numRead=0;
		while((numRead=reader.read(buf)) != -1){
			fileData.append(buf, 0, numRead);
		}
		reader.close();
		return fileData.toString();
	}

	private boolean run(final Ruby runtime, final String script) {
		getLog().info("Running RSpec tests from " + sourceDirectory);
		RubyRuntimeAdapter evaler = JavaEmbedUtils.newRuntimeAdapter();
		IRubyObject o = evaler.eval(runtime, script.toString());
		boolean result = ((RubyBoolean) JavaEmbedUtils.invokeMethod(runtime, o,
				"run", new Object[] { sourceDirectory, requiredModules, reportFile(), progressFile() },
				(Class<?>) RubyBoolean.class)).isTrue();
		return result;
	}

	private String script(final Ruby runtime) throws MojoExecutionException {
		StringBuilder script = new StringBuilder();
		try {
			script.append(handleClasspathElements(runtime));
		} catch (MalformedURLException e) {
			throw new MojoExecutionException(e.getMessage());
		}

		script.append(loadRubyScript());
		return script.toString();
	}

	private String reportFile() {
		return outputDirectory + "/" + reportName + ".html";
	}

	private String progressFile() {
		return outputDirectory + "/" + reportName + ".txt";
	}

	private Ruby ruby() {
		Ruby runtime = Ruby.newInstance();
		getLog().info("JRuby Home: " + jrubyHome);
		runtime.setJRubyHome(jrubyHome);
		runtime.getLoadService().init(classpathElements);
		return runtime;
	}

	private void validateRubyHome() throws MojoExecutionException {
		if (jrubyHome == null) {
			throw new MojoExecutionException(
					"$JRUBY_HOME or jrubyHome directory not specified");
		}
	}

	private String loadRubyScript() {
		return new Scanner(Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("RSpecRunner.rb")).useDelimiter("\\Z")
				.next();
	}

	private String handleClasspathElements(Ruby runtime)
			throws MalformedURLException {
		StringBuilder script = new StringBuilder();
		for (String path : classpathElements) {
			if (path.endsWith(".jar")) {
				// handling jar files
				script.append("require '").append(path).append("'\n");
			} else {
				// handling directories
				getLog().info("Adding to Ruby Class Loader: " + path);
				runtime.getJRubyClassLoader().addURL(
						new URL("file:" + path + "/"));
			}
		}
		return script.toString();
	}

}
