/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001-2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Ant", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.tools.ant.taskdefs.optional.ejb;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.BuildException;

/**
 * This class is used to generate iPlanet Application Server (iAS) 6.0 stubs and
 * skeletons and build an EJB Jar file.  It is designed to be used with the Ant
 * <code>ejbjar</code> task.  If only stubs and skeletons need to be generated
 * (in other words, if no JAR file needs to be created), refer to the
 * <code>iplanet-ejbc</code> task and the <code>IPlanetEjbcTask</code> class.
 * <p>
 * The following attributes may be specified by the user:
 *   <ul>
 *     <li><i>destdir</i> -- The base directory into which the generated JAR
 *                           files will be written.  Each JAR file is written
 *                           in directories which correspond to their location
 *                           within the "descriptordir" namespace.  This is a
 *                           required attribute.
 *     <li><i>classpath</i> -- The classpath used when generating EJB stubs and
 *                             skeletons.  This is an optional attribute (if
 *                             omitted, the classpath specified in the "ejbjar"
 *                             parent task will be used).  If specified, the
 *                             classpath elements will be prepended to the
 *                             classpath specified in the parent "ejbjar" task.
 *                             Note that nested "classpath" elements may also be
 *                             used.
 *     <li><i>keepgenerated</i> -- Indicates whether or not the Java source
 *                                 files which are generated by ejbc will be
 *                                 saved or automatically deleted.  If "yes",
 *                                 the source files will be retained.  This is
 *                                 an optional attribute (if omitted, it
 *                                 defaults to "no").
 *     <li><i>debug</i> -- Indicates whether or not the ejbc utility should
 *                         log additional debugging statements to the standard
 *                         output.  If "yes", the additional debugging statements
 *                         will be generated (if omitted, it defaults to "no").
 *     <li><i>iashome</i> -- May be used to specify the "home" directory for
 *                           this iPlanet Application server installation.  This
 *                           is used to find the ejbc utility if it isn't
 *                           included in the user's system path.  This is an
 *                           optional attribute (if specified, it should refer
 *                           to the <code>[install-location]/iplanet/ias6/ias
 *                           </code> directory).  If omitted, the ejbc utility
 *                           must be on the user's system path.
 *     <li><i>suffix</i> -- String value appended to the JAR filename when
 *                          creating each JAR.  This attribute is not required
 *                          (if omitted, it defaults to ".jar").
 *   </ul>
 * <p>
 * For each EJB descriptor found in the "ejbjar" parent task, this deployment
 * tool will locate the three classes that comprise the EJB.  If these class
 * files cannot be located in the specified <code>srcdir</code> directory, the
 * task will fail.  The task will also attempt to locate the EJB stubs and
 * skeletons in this directory.  If found, the timestamps on the stubs and
 * skeletons will be checked to ensure they are up to date.  Only if these files
 * cannot be found or if they are out of date will ejbc be called.
 *
 * @see    IPlanetEjbc
 * @author Greg Nelson <a href="mailto:greg@netscape.com">greg@netscape.com</a>
 */
public class IPlanetDeploymentTool extends GenericDeploymentTool {

    /* Attributes set by the Ant build file */
    private File    iashome;
    private String  jarSuffix     = ".jar";
    private boolean keepgenerated = false;
    private boolean debug         = false;

    /*
     * Filenames of the standard EJB descriptor (which is passed to this class
     * from the parent "ejbjar" task) and the iAS-specific EJB descriptor
     * (whose name is determined by this class).  Both filenames are relative
     * to the directory specified by the "srcdir" attribute in the ejbjar task.
     */
    private String  descriptorName;
    private String  iasDescriptorName;

    /*
     * The displayName variable stores the value of the "display-name" element
     * from the standard EJB descriptor.  As a future enhancement to this task,
     * we may determine the name of the EJB JAR file using this display-name,
     * but this has not be implemented yet.
     */
    private String  displayName;

    /*
     * Regardless of the name of the iAS-specific EJB descriptor file, it will
     * written in the completed JAR file as "ias-ejb-jar.xml".  This is the
     * naming convention implemented by iAS.
     */
    private static final String IAS_DD = "ias-ejb-jar.xml";

    /**
     * Setter method used to store the "home" directory of the user's iAS
     * installation.  The directory specified should typically be
     * <code>[install-location]/iplanet/ias6/ias</code>.
     *
     * @param iashome The home directory for the user's iAS installation.
     */
    public void setIashome(File iashome) {
        this.iashome = iashome;
    }

    /**
     * Setter method used to specify whether the Java source files generated by
     * the ejbc utility should be saved or automatically deleted.
     *
     * @param keepgenerated boolean which, if <code>true</code>, indicates that
     *                      Java source files generated by ejbc for the stubs
     *                      and skeletons should be kept.
     */
    public void setKeepgenerated(boolean keepgenerated) {
        this.keepgenerated = keepgenerated;
    }

    /**
     * Sets whether or not debugging output will be generated when ejbc is
     * executed.
     *
     * @param debug A boolean indicating if debugging output should be generated
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Setter method used to specify the filename suffix (for example, ".jar")
     * for the JAR files to be created.
     *
     * @param jarSuffix The string to use as the JAR filename suffix.
     */
    public void setSuffix(String jarSuffix) {
        this.jarSuffix = jarSuffix;
    }

    /**
     * Since iAS doesn't generate a "generic" JAR as part of its processing,
     * this attribute is ignored and a warning message is displayed to the user.
     *
     * @param inString the string to use as the suffix.  This parameter is
     *                 ignored.
     */
    public void setGenericJarSuffix(String inString) {
        log("Since a generic JAR file is not created during processing, the "
                + "iPlanet Deployment Tool does not support the "
                + "\"genericjarsuffix\" attribute.  It will be ignored.",
            Project.MSG_WARN);
    }

    public void processDescriptor(String descriptorName, SAXParser saxParser) {
        this.descriptorName = descriptorName;
        this.iasDescriptorName = null;

        log("iPlanet Deployment Tool processing: " + descriptorName + " (and "
                + getIasDescriptorName() + ")", Project.MSG_VERBOSE);

        super.processDescriptor(descriptorName, saxParser);
    }

    /**
     * Verifies that the user selections are valid.
     *
     * @param descriptorFileName String representing the file name of an EJB
     *                           descriptor to be processed
     * @param saxParser          SAXParser which may be used to parse the XML
     *                           descriptor
     * @throws BuildException If the user selections are invalid.
     */
    protected void checkConfiguration(String descriptorFileName,
                                    SAXParser saxParser) throws BuildException {

        int startOfName = descriptorFileName.lastIndexOf(File.separatorChar) + 1;
        String stdXml = descriptorFileName.substring(startOfName);
        if (stdXml.equals(EJB_DD) && (getConfig().baseJarName == null)) {
            String msg = "No name specified for the completed JAR file.  The EJB"
                            + " descriptor should be prepended with the JAR "
                            + "name or it should be specified using the "
                            + "attribute \"basejarname\" in the \"ejbjar\" task.";
            throw new BuildException(msg, getLocation());
        }

        File iasDescriptor = new File(getConfig().descriptorDir,
                                        getIasDescriptorName());
        if ((!iasDescriptor.exists()) || (!iasDescriptor.isFile())) {
            String msg = "The iAS-specific EJB descriptor ("
                            + iasDescriptor + ") was not found.";
            throw new BuildException(msg, getLocation());
        }

        if ((iashome != null) && (!iashome.isDirectory())) {
            String msg = "If \"iashome\" is specified, it must be a valid "
                            + "directory (it was set to " + iashome + ").";
            throw new BuildException(msg, getLocation());
        }
    }

    /**
     * This method returns a list of EJB files found when the specified EJB
     * descriptor is parsed and processed.
     *
     * @param descriptorFileName String representing the file name of an EJB
     *                           descriptor to be processed
     * @param saxParser          SAXParser which may be used to parse the XML
     *                           descriptor
     * @return                   Hashtable of EJB class (and other) files to be
     *                           added to the completed JAR file
     * @throws IOException       An IOException from the parser, possibly from
     *                           the byte stream or character stream
     * @throws SAXException      Any SAX exception, possibly wrapping another
     *                           exception
     */
    protected Hashtable parseEjbFiles(String descriptorFileName,
                         SAXParser saxParser) throws IOException, SAXException {

        Hashtable files;

        /* Build and populate an instance of the ejbc utility */
        IPlanetEjbc ejbc = new IPlanetEjbc(
                                    new File(getConfig().descriptorDir,
                                                descriptorFileName),
                                    new File(getConfig().descriptorDir,
                                                getIasDescriptorName()),
                                    getConfig().srcDir,
                                    getCombinedClasspath().toString(),
                                    saxParser);
        ejbc.setRetainSource(keepgenerated);
        ejbc.setDebugOutput(debug);
        if (iashome != null) {
            ejbc.setIasHomeDir(iashome);
        }

        /* Execute the ejbc utility -- stubs/skeletons are rebuilt, if needed */
        try {
            ejbc.execute();
        } catch (IPlanetEjbc.EjbcException e) {
            throw new BuildException("An error has occurred while trying to "
                        + "execute the iAS ejbc utility", e, getLocation());
        }

        displayName    = ejbc.getDisplayName();
        files          = ejbc.getEjbFiles();

        /* Add CMP descriptors to the list of EJB files */
        String[] cmpDescriptors = ejbc.getCmpDescriptors();
        if (cmpDescriptors.length > 0) {
            File baseDir = getConfig().descriptorDir;

            int endOfPath = descriptorFileName.lastIndexOf(File.separator);
            String relativePath = descriptorFileName.substring(0, endOfPath + 1);

            for (int i = 0; i < cmpDescriptors.length; i++) {
                int endOfCmp = cmpDescriptors[i].lastIndexOf('/');
                String cmpDescriptor = cmpDescriptors[i].substring(endOfCmp + 1);

                File   cmpFile = new File(baseDir, relativePath + cmpDescriptor);
                if (!cmpFile.exists()) {
                    throw new BuildException("The CMP descriptor file ("
                            + cmpFile + ") could not be found.", getLocation());
                }
                files.put(cmpDescriptors[i], cmpFile);
            }
        }

        return files;
    }

    /**
     * Add the iAS-specific EJB descriptor to the list of files which will be
     * written to the JAR file.
     *
     * @param ejbFiles Hashtable of EJB class (and other) files to be added to
     *                 the completed JAR file.
     * @param baseName String name of the EJB JAR file to be written (without
     *                 a filename extension).
     */
    protected void addVendorFiles(Hashtable ejbFiles, String ddPrefix) {
        ejbFiles.put(META_DIR + IAS_DD, new File(getConfig().descriptorDir,
                     getIasDescriptorName()));
    }

    /**
     * Get the name of the Jar that will be written. The modification date
     * of this jar will be checked against the dependent bean classes.
     *
     * @param baseName String name of the EJB JAR file to be written (without
     *                 a filename extension).
     *
     * @return File representing the JAR file which will be written.
     */
    File getVendorOutputJarFile(String baseName) {
        File jarFile = new File(getDestDir(), baseName + jarSuffix);
        log("JAR file name: " + jarFile.toString(), Project.MSG_VERBOSE);
        return jarFile;
    }

    /**
     * The iAS ejbc utility doesn't require the Public ID of the descriptor's
     * DTD for it to process correctly--this method always returns <code>null
     * </code>.
     *
     * @return <code>null</code>.
     */
    protected String getPublicId() {
        return null;
    }

    /**
     * Determines the name of the iAS-specific EJB descriptor using the
     * specified standard EJB descriptor name.  In general, the standard
     * descriptor will be named "[basename]-ejb-jar.xml", and this method will
     * return "[basename]-ias-ejb-jar.xml".
     *
     * @return The name of the iAS-specific EJB descriptor file.
     */
    private String getIasDescriptorName() {

        /* Only calculate the descriptor name once */
        if (iasDescriptorName != null) {
            return iasDescriptorName;
        }

        String path = "";   // Directory path of the EJB descriptor
        String basename;    // Filename appearing before name terminator
        String remainder;   // Filename appearing after the name terminator

        /* Find the end of the standard descriptor's relative path */
        int startOfFileName = descriptorName.lastIndexOf(File.separatorChar);
        if (startOfFileName != -1) {
            path = descriptorName.substring(0, startOfFileName + 1);
        }

        /* Check to see if the standard name is used (there's no basename) */
        if (descriptorName.substring(startOfFileName + 1).equals(EJB_DD)) {
            basename = "";
            remainder = EJB_DD;

        } else {
            int endOfBaseName = descriptorName.indexOf(
                                                getConfig().baseNameTerminator,
                                                startOfFileName);
            /*
             * Check for the odd case where the terminator and/or filename
             * extension aren't found.  These will ensure "ias-" appears at the
             * end of the name and before the '.' (if present).
             */
            if (endOfBaseName < 0) {
                endOfBaseName = descriptorName.lastIndexOf('.') - 1;
                if (endOfBaseName < 0) {
                    endOfBaseName = descriptorName.length() - 1;
                }
            }

            basename = descriptorName.substring(startOfFileName + 1,
                                                endOfBaseName + 1);
            remainder = descriptorName.substring(endOfBaseName + 1);
        }

        iasDescriptorName = path + basename + "ias-" + remainder;
        return iasDescriptorName;
    }
}
