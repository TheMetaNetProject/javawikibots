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
public class MetaphorImporter {

    private static final Logger logger = Logger.getLogger("MetaphorImporter");
    
    private static final String login = "metabot";
    private static final String pword = "XrgQf9.DDt4!";
    private static final String wikiServer = "metaphor.icsi.berkeley.edu";
//    private static final String wikiBase = "/test";
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
            HashSet<String> addedSchemas = new HashSet<String>();
            HashSet<String> addedMetaphors = new HashSet<String>();

            //Read File Line By Line
            while ((strLine = br.readLine()) != null) {

                // trim whitespace
                strLine = strLine.trim();

                String sourceSchema;
                String targetSchema;

                if (strLine.equals("") || strLine.startsWith("---")) {
                    continue;
                }
                if (strLine.startsWith("-")) {
                    // this one is special, do something
                    // then remove the "-"
                    strLine = strLine.substring(1).trim();
                }
                // remove trailing space/punctuation for split
                String[] words = strLine.replaceAll("\\s*\\p{Punct}+$", "").split("\\s+");
                if (strLine.endsWith("-:")) {
                    // then the order is reversed SOURCE TARGET
                    targetSchema = words[0];
                    sourceSchema = words[1];
                } else {
                    // the order is TARGET SOURCE
                    sourceSchema = words[0];
                    targetSchema = words[1];
                }
                String isExpr = " IS ";
                if (lang.equals("es")) {
                    isExpr = " ES ";
                }
                String metaphor = "AutoMetaphor:" + targetSchema.toUpperCase(locale) + isExpr + sourceSchema.toUpperCase(locale);
                targetSchema = "AutoSchema:" + targetSchema.substring(0, 1).toUpperCase(locale) + targetSchema.substring(1);
                sourceSchema = "AutoSchema:" + sourceSchema.substring(0, 1).toUpperCase(locale) + sourceSchema.substring(1);

                System.out.println(metaphor);
                System.out.println(targetSchema + " <= " + sourceSchema);

                try {
                    if (!addedMetaphors.contains(metaphor)) {
                        wiki.edit(metaphor, getNewMetaphorContent(sourceSchema, targetSchema), "import of autoextracted metaphor", false, true, -2, null);
                        addedMetaphors.add(metaphor);
                    }
                    if (!addedSchemas.contains(targetSchema)) {
                        wiki.edit(targetSchema, getNewSchemaContent(), "import of autoextracted schema", false, true, -2, null);
                        addedSchemas.add(targetSchema);
                    }
                    if (!addedSchemas.contains(sourceSchema)) {
                        wiki.edit(sourceSchema, getNewSchemaContent(), "import of autoextracted schema", false, true, -2, null);
                        addedSchemas.add(sourceSchema);
                    }
                } catch (LoginException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

        } catch (IOException io) {
            System.err.println("Error reading file: " + io);
        }



    }

    private static String getNewSchemaContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("{{AutoSchema\n");
        sb.append("|Entered by=");
        sb.append(login);
        sb.append("\n");
        sb.append("|Status=auto extracted\n");
        sb.append("}}\n");
        return sb.toString();
    }

    private static String getNewMetaphorContent(String source, String target) {
        StringBuilder sb = new StringBuilder();
        sb.append("{{AutoMetaphor\n");
        sb.append("|Entered by=");
        sb.append(login);
        sb.append("\n");
        sb.append("|Status=auto extracted\n");
        sb.append("|Source schema=");
        sb.append(source);
        sb.append("|Target schema=");
        sb.append(target);
        sb.append("}}\n");
        return sb.toString();
    }
}
