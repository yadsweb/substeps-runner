/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.technophobia.substeps.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.MojoExecutionException;

/**
 *
 * @author Blast
 */
public class TagRetriver {

    static String main_tag = "";

    static List<String> result = new ArrayList();

    static final Logger log = Logger.getLogger(TagRetriver.class.getName());

    static void Process(File aFile) {

        if (aFile.isFile()) {

            log.log(Level.INFO, "Processing file with name: {0}", aFile.getName());

            try {
                try (BufferedReader br = new BufferedReader(new FileReader(aFile))) {
                    StringBuilder sb = new StringBuilder();
                    String line = br.readLine();

                    while (line != null) {
                        sb.append(line);
                        sb.append("\n");
                        line = br.readLine();
                        if (sb.toString().contains(main_tag)) {
                            log.log(Level.INFO, "File contain tag (" + main_tag + ") is: {0}", aFile.getName());
                            Pattern pattern = Pattern.compile("@(.*?)@");
                            Matcher matcher = pattern.matcher(sb.toString());
                            if (matcher.find()) {
                                log.log(Level.INFO, "Tag added to the result is: {0}", matcher.group(1));
                                result.add(matcher.group(1));
                                break;
                            }

                        }

                    }

                }
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error when reading the feature file: {0}", aFile.getName());
            }

        } else {
            if (aFile.isDirectory()) {

                File[] listOfFiles = aFile.listFiles();
                if (listOfFiles != null) {
                    for (File listOfFile : listOfFiles) {
                        Process(listOfFile);
                    }
                } else {
                    log.log(Level.INFO, "Access dedied for directory: {0}", aFile.getName());
                }
            }
        }
    }

    public List<String> retrive(List<ExecutionConfig> cfg) throws MojoExecutionException {

        main_tag = cfg.get(0).asSubstepsExecutionConfig().getTags();
        String feature_files = cfg.get(0).asSubstepsExecutionConfig().getFeatureFile();
        log.log(Level.INFO, "Main tag for current execu: {0}", main_tag);
        log.log(Level.INFO, "Feature files for current executions are in: {0}", feature_files);

        File dir = new File(feature_files);

        Process(dir);

        log.log(Level.INFO, "Found {0} unique tags.", result.size());
        return result;
    }

}
