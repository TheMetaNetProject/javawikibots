/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.berkeley.icsi.metaphor.javawikibots;

import java.io.BufferedReader;
import java.io.Console;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import org.wikipedia.Wiki;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Bot that imports metaphors into the Wiki
 *
 * @author jhong
 */
public class LinguisticMetaphorImporter {

    private static final Logger logger = Logger.getLogger("LinguisticMetaphorImporter");

    private static final String enteredby = "Metabot";

//    private static final String login = "metabot";
//    private static final String pword = "XrgQf9.DDt4!";
    private static final String wikiServer = "metaphor.icsi.berkeley.edu";
    private static final Set<String> wikis = new HashSet<String>();
    
    static {
        wikis.add("en");
        wikis.add("es");
        wikis.add("fa");
        wikis.add("ru");
        wikis.add("test");
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String lang = null;
        String wikiuser = null;
        String wikipw = null;
        String inputFile = null;
        
        Options options = new Options();
        options.addOption("l", "lang", true, "Wiki language code (en, es, fa, ru)");
        options.addOption("u", "user", true, "Wiki username");
        options.addOption("p", "password", true, "Wiki password");
        options.addOption("v", "verbose", false, "Display verbose messages");

        Level logLevel = Level.SEVERE;
        
        /*
         * Parse Command Line options
         */
        CommandLineParser cparser = new PosixParser();
        try {
            CommandLine cmd = cparser.parse(options, args);
            lang = cmd.getOptionValue("lang");
            if (!wikis.contains(lang)) {
                throw new ParseException("Invalid lang parameter: "+lang);
            }
            
            String user = cmd.getOptionValue("user");
            String pw = cmd.getOptionValue("password");

            // if user/password were not given as parameters, read from Console
            if (user == null) {
                Console cons;
                if ((cons = System.console()) != null
                        && (user = cons.readLine("[%s]", "Username:")) != null) {
                    wikiuser = user;
                } else {
                    throw new ParseException("Error: Must specify wiki password.");
                }
            } else {
                wikiuser = user;
            }
            if (pw == null) {
                Console cons;
                char[] passwd;
                if ((cons = System.console()) != null
                        && (passwd = cons.readPassword("[%s]", "Password:")) != null) {
                    wikipw = new String(passwd);
                } else {
                    throw new ParseException("Error: Must specify wiki password.");
                }
            } else {
                wikipw = pw;
            }
            String[] remainingArgs = cmd.getArgs();
            inputFile = remainingArgs[0];
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, "Error parsing command line parameters", ex);
            System.exit(1);
        }
        // initialize logging levels
        logger.setLevel(logLevel);

        // log in to Wiki
        Wiki wiki = new Wiki(wikiServer, "/"+lang);
        try {
            wiki.login(wikiuser, wikipw.toCharArray());
            wiki.setThrottle(10); //miliseconds
            wiki.setLogLevel(logLevel);
        } catch (FailedLoginException fe) {
            System.err.println("Login error: " + fe);
        } catch (IOException io) {
            System.err.println("IO Error: " + io);
        }

        Locale locale = new Locale(lang);

        // open up file to import
        try {
            FileInputStream infile = new FileInputStream(inputFile);
            DataInputStream in = new DataInputStream(infile);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String strLine;

            // keep track so as not to re-create/edit the same schemas/metaphors again
            HashSet<String> addedMetaphors = new HashSet<String>();

            String seedMetaphor = null;
            
            //Read File Line By Line
            while ((strLine = br.readLine()) != null) {
                boolean thisIsSeed = false;
                boolean overwriteExisting = false;

                // trim whitespace
                strLine = strLine.trim();

                String source;
                String target;

                if (strLine.equals("") || strLine.startsWith("---")) {
                    continue;
                }
                if (strLine.startsWith("-")) {
                    // this one is special, do something
                    // then remove the "-"
                    strLine = strLine.substring(1).trim();
                    thisIsSeed = true;
                }
                
                // remove trailing space/punctuation for split
                String[] words = strLine.replaceAll("\\s*\\p{Punct}+$", "").split("\\s+");
                
                if (strLine.endsWith("-:")) {
                    // then the order is reversed SOURCE TARGET
                    target = words[0];
                    source = words[1];
                } else {
                    // the order is TARGET SOURCE
                    source = words[0];
                    target = words[1];
                }
                // For these cases, source = v, target = n.
                source = source + ".v";
                target = target + ".n";
                
                String metaphor = "Linguistic metaphor:" 
                        + words[0].substring(0, 1).toUpperCase(locale) 
                        + words[0].substring(1) + " " + words[1];
                String type = "extracted";
                
                if (thisIsSeed) {
                    type = "seed";
                    seedMetaphor = metaphor;
                } else {
                    if (metaphor.equals(seedMetaphor)) {
                        //extracted and seed metaphors are same
                        //(seed used to extract examples)
                        type = "seed, extracted";
                        overwriteExisting = true;
                    }
                }

                System.out.println(metaphor);

                try {
                    if (overwriteExisting || !addedMetaphors.contains(metaphor)) {
                        wiki.edit(metaphor,
                                getNewMetaphorContent(source, target, type, seedMetaphor), "import of autoextracted metaphor", false, true, -2, null);
                        addedMetaphors.add(metaphor);
                    }
                } catch (LoginException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

        } catch (IOException io) {
            System.err.println("Error reading file: " + io);
        }



    }

    private static String getNewMetaphorContent(String source, String target, String type, String seed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{{Linguistic metaphor");
        sb.append("\n|Type=");
        sb.append(type);
        // specify seed only if purely extracted
        if (type.equals("extracted")) {
            sb.append("\n|Seed=");
            sb.append(seed);
        }
        sb.append("\n|Source=");
        sb.append(source);
        sb.append("\n|Target=");
        sb.append(target);
        sb.append("\n|Examples=");
        sb.append("\n|Entered by=");
        sb.append(enteredby);
        sb.append("\n|Status=auto imported");
        sb.append("\n}}\n");
        return sb.toString();
    }
}
