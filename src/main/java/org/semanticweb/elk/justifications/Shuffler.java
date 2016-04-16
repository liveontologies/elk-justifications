package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Shuffler {

	public static void main(final String[] args) {
		
		if (args.length < 1) {
			System.err.println("Insufficient arguments!");
			System.exit(1);
		}
		Long.valueOf(args[0]);
		final Random random = new Random(Long.valueOf(args[0]));
		
		FileInputStream fin = null;
		
		try {
			final InputStream in;
			if (args.length > 1) {
				fin = new FileInputStream(args[1]);
				in = fin;
			} else {
				in = System.in;
			}
			
			final List<String> lines = new ArrayList<String>();
			
			final BufferedReader reader =
					new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			
			Collections.shuffle(lines, random);
			
			for (final String l : lines) {
				System.out.println(l);
			}
			
		} catch (final FileNotFoundException e) {
			System.err.println("The input file cannot be found!");
			e.printStackTrace();
			System.exit(2);
		} catch (final IOException e) {
			System.err.println("Problem during reading the imput file!");
			e.printStackTrace();
			System.exit(2);
		} finally {
			if (fin != null) {
				try {
					fin.close();
				} catch (final IOException e) {}
			}
		}
		
	}

}
