package SIMPLAnalyser;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WSLCommandRunner {

	public static int syncSafely(String syncCommand) {
		// Default to error unless set
		int exitCode = -1; 
		boolean noFileFlag = false;
		
		try {
			// For logging
			System.out.println("Executing command: " + syncCommand);
			
			// This opens WSL and runs a command
	        ProcessBuilder builder = new ProcessBuilder("wsl.exe", "bash", "-c", syncCommand);

            // Combine stderr and stdout
            builder.redirectErrorStream(true);
            Process process = builder.start();

            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contentEquals("No local file to upload")) {
                	noFileFlag = true;
                }
            }

            // Wait for command to finish to get exit code
            if (!noFileFlag) {
            	// Use returned exit code
            	exitCode = process.waitFor();
            } else {
            	// Override behaviour to treat no files as failure
            	exitCode = -2;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            
        } 
		
		if (exitCode == -2) {
			System.out.println("No local files were found!");
			System.out.println("Command exit code: " + exitCode);
		} else {
			System.out.println("Command exit code: " + exitCode);
		}
			
		return exitCode;

	}

}
