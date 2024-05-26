import java.util.Arrays;

import soot.PackManager;
import soot.Transform;

import analyses.*;

public class Main {
	public static void main (String [] args) {
		String classPath = "samples";
		String mainClass = "Main";
		
		String [] sootArgs = {
				"-v",
				"-cp", classPath,
				"-pp",
				"-w", /*"-app",*/
				//"-p", "jb", "use-original-names:true",
				//"-p", "cg.cha", "enabled:true",
				//"-p", "cg.spark", "enabled:false",
				//"-f", "J",
				//"-d", "output",
				"-keep-bytecode-offset",
				"-keep-line-number",
				// "-p", "jb", "preserve-source-annotations:true",
				// //"-p", "jb", "stabilize-local-names:true",
				// "-p", "jb.ulp", "enabled:false",
				// "-p", "jb.dae", "enabled:false",
				// "-p", "jb.cp-ule", "enabled:false",
				// "-p", "jb.cp", "enabled:false",
				// "-p", "jb.lp", "enabled:false",
				// //"-p", "jb.lns", "enabled:false",
				// "-p", "jb.dtr", "enabled:false",
				// "-p", "jb.ese", "enabled:false",
				// //"-p", "jb.sils", "enabled:false",
				// "-p", "jb.a", "enabled:false",
				// "-p", "jb.ule", "enabled:false",
				// "-p", "jb.ne", "enabled:false",
				// //"-p", "jb.uce", "enabled:false",
				// "-p", "jb.tt", "enabled:false",
				// "-p", "bb.lp", "enabled:false",
				// "-p", "jop", "enabled:false",
				// "-p", "bop", "enabled:false",
				// "-java-version", "1.8",
				mainClass
				
		};
		
		System.out.println("The soot arguments are " + Arrays.toString(sootArgs));
		
		LoopAnalysis la = new LoopAnalysis();
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.la", la));
		
		soot.Main.main(sootArgs);

	}
}