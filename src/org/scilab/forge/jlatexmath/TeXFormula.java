/* TeXFormula.java
 * =========================================================================
 * This file is originally part of the JMathTeX Library - http://jmathtex.sourceforge.net
 *
 * Copyright (C) 2004-2007 Universiteit Gent
 * Copyright (C) 2009 DENIZET Calixte
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License can be found in the file
 * LICENSE.txt provided with the source distribution of this program (see
 * the META-INF directory in the source jar). This license can also be
 * found on the GNU website at http://www.gnu.org/licenses/gpl.html.
 *
 * If you did not receive a copy of the GNU General Public License along
 * with this program, contact the lead developer, or write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 *
 */

/* Modified by Calixte Denizet */

package org.scilab.forge.jlatexmath;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.io.InputStream;

/**
 * Represents a logical mathematical formula that will be displayed (by creating a
 * {@link TeXIcon} from it and painting it) using algorithms that are based on the
 * TeX algorithms.
 * <p>
 * These formula's can be built using the built-in primitive TeX parser
 * (methods with String arguments) or using other TeXFormula objects. Most methods
 * have (an) equivalent(s) where one or more TeXFormula arguments are replaced with
 * String arguments. These are just shorter notations, because all they do is parse
 * the string(s) to TeXFormula's and call an equivalent method with (a) TeXFormula argument(s).
 * Most methods also come in 2 variants. One kind will use this TeXFormula to build
 * another mathematical construction and then change this object to represent the newly
 * build construction. The other kind will only use other
 * TeXFormula's (or parse strings), build a mathematical construction with them and
 * insert this newly build construction at the end of this TeXFormula.
 * Because all the provided methods return a pointer to this (modified) TeXFormula
 * (except for the createTeXIcon method that returns a TeXIcon pointer),
 * method chaining is also possible.
 * <p>
 * <b> Important: All the provided methods modify this TeXFormula object, but all the
 * TeXFormula arguments of these methods will remain unchanged and independent of
 * this TeXFormula object!</b>
 */
public class TeXFormula {
    
    // table for putting delimiters over and under formula's,
    // indexed by constants from "TeXConstants"
    private static final String[][] delimiterNames = {
        { "lbrace", "rbrace" },
        { "lsqbrack", "rsqbrack" },
        { "lbrack", "rbrack" },
        { "downarrow", "downarrow" },
        { "uparrow", "uparrow" },
        { "updownarrow", "updownarrow" },
        { "Downarrow", "Downarrow" },
        { "Uparrow", "Uparrow" },
        { "Updownarrow", "Updownarrow" },
        { "vert", "vert" },
        { "Vert", "Vert" }
    };

    // used as second index in "delimiterNames" table (over or under)
    private static final int OVER_DEL = 0;
    private static final int UNDER_DEL = 1;
    
    // for comparing floats with 0
    protected static final float PREC = 0.0000001f;
    
    // predefined TeXFormula's
    public static Map<String,TeXFormula> predefinedTeXFormulas = new HashMap<String,TeXFormula>();
    
    // character-to-symbol and character-to-delimiter mappings
    public static String[] symbolMappings;
    public static Atom[] symbolFormulaMappings;
    public MiddleAtom middle = null;
    
    static {
        // character-to-symbol and character-to-delimiter mappings
        TeXFormulaSettingsParser parser = new TeXFormulaSettingsParser();
        symbolMappings = parser.parseSymbolMappings();
        
        // predefined TeXFormula's
	try {
	    new PredefinedTeXFormulaParser("PredefinedCommands.xml", "Command").parse(MacroInfo.Commands);
	    new PredefinedTeXFormulaParser("PredefinedTeXFormulas.xml", "TeXFormula").parse(predefinedTeXFormulas);
	} catch (Exception e) {
	    System.out.println(e.toString());
	}

	symbolFormulaMappings = parser.parseSymbolToFormulaMappings();
    }
    
    // the root atom of the "atom tree" that represents the formula
    public Atom root = null;
    
    // the current text style
    public String textStyle = null;

    public boolean isColored = false;
    
    /**
     * Creates an empty TeXFormula.
     *
     */
    public TeXFormula() {
        // do nothing
    }

    /**
     * Creates a new TeXFormula by parsing the given string (using a primitive TeX parser).
     *
     * @param s the string to be parsed
     * @throws ParseException if the string could not be parsed correctly
     */
    public TeXFormula(String s) throws ParseException {
        this(s, null);
    }

    public TeXFormula(String s, boolean firstpass) throws ParseException {
	this.textStyle = null;
	TeXParser tp = new TeXParser(s, this, firstpass);
        if (s != null && s.length() != 0)
            tp.parse();
    }
    
   /*
    * Creates a TeXFormula by parsing the given string in the given text style.
    * Used when a text style command was found in the parse string.
    */
    public TeXFormula(String s, String textStyle) throws ParseException {
        this.textStyle = textStyle;
	TeXParser tp = new TeXParser(s, this);
        if (s != null && s.length() != 0)
            tp.parse();
    }

    public TeXFormula(String s, String textStyle, boolean firstpass, boolean space) throws ParseException {
        this.textStyle = textStyle;
	TeXParser tp = new TeXParser(s, this, firstpass, space);
        if (s != null && s.length() != 0)
            tp.parse();
    }
    
    /**
     * Creates a new TeXFormula that is a copy of the given TeXFormula.
     * <p>
     * <b>Both TeXFormula's are independent of one another!</b>
     *
     * @param f the formula to be copied
     */
    public TeXFormula(TeXFormula f) {
        if (f != null)
            addImpl(f);
    }
    
   /*
    * Inserts an atom at the end of the current formula
    */
    public TeXFormula add(Atom el) {
        if (el != null) {
	    if (el instanceof MiddleAtom)
		middle = (MiddleAtom)el;
            if (root == null)
                root = el;
            else {
                if (!(root instanceof RowAtom))
                    root = new RowAtom(root);
                ((RowAtom) root).add(el);
            }
        }
        return this;
    }

    /**
     * Parses the given string and inserts the resulting formula
     * at the end of the current TeXFormula.
     *
     * @param s the string to be parsed and inserted
     * @throws ParseException if the string could not be parsed correctly
     * @return the modified TeXFormula
     */
    public TeXFormula add(String s) throws ParseException {
        if (s != null && s.length() != 0) {
            // reset parsing variables
            textStyle = null;
            // parse and add the string
            add(new TeXFormula(s));
        }
        return this;
    }
    
    public TeXFormula append(String s) throws ParseException {
	if (s != null && s.length() != 0) {
            TeXParser tp = new TeXParser(s, this);
	    tp.parse();
        }
        return this;
    }
    
    /**
     * Inserts the given TeXFormula at the end of the current TeXFormula.
     *
     * @param f the TeXFormula to be inserted
     * @return the modified TeXFormula
     */
    public TeXFormula add(TeXFormula f) {
        addImpl (f);
        return this;
    }
    
    private void addImpl (TeXFormula f) {
        if (f.root != null) {
            // special copy-treatment for Mrow as a root!!
            if (f.root instanceof RowAtom)
                add(new RowAtom(f.root));
            else
                add(f.root);
        }
    }
    
    public void setLookAtLastAtom(boolean b) {
	if (root instanceof RowAtom)
	    ((RowAtom)root).lookAtLastAtom = b;
    }

    public boolean getLookAtLastAtom() {
	if (root instanceof RowAtom)
	    return ((RowAtom)root).lookAtLastAtom;
	return false;
    }
    
    /**
     * Centers the current TeXformula vertically on the axis (defined by the parameter
     * "axisheight" in the resource "DefaultTeXFont.xml".
     *
     * @return the modified TeXFormula
     */
    public TeXFormula centerOnAxis() {
        root = new VCenteredAtom(root);
        return this;
    }
    
    public static void addPredefinedTeXFormula(InputStream xmlFile) throws ResourceParseException {
	new PredefinedTeXFormulaParser(xmlFile, "TeXFormula").parse(predefinedTeXFormulas);
    }
    
    public static void addPredefinedCommands(InputStream xmlFile) throws ResourceParseException {
	new PredefinedTeXFormulaParser(xmlFile, "Command").parse(MacroInfo.Commands);
    }
    
    /**
     * Inserts a strut box (whitespace) with the given width, height and depth (in
     * the given unit) at the end of the current TeXFormula.
     *
     * @param unit a unit constant (from {@link TeXConstants})
     * @param width the width of the strut box
     * @param height the height of the strut box
     * @param depth the depth of the strut box
     * @return the modified TeXFormula
     * @throws InvalidUnitException if the given integer value does not represent
     * 			a valid unit
     */
    public TeXFormula addStrut(int unit, float width, float height, float depth)
    throws InvalidUnitException {
        return add(new SpaceAtom(unit, width, height, depth));
    }
    
    /**
     * Inserts a strut box (whitespace) with the given width, height and depth (in
     * the given unit) at the end of the current TeXFormula.
     *
     * @param type thinmuskip, medmuskip or thickmuskip (from {@link TeXConstants})
     * @return the modified TeXFormula
     * @throws InvalidUnitException if the given integer value does not represent
     * 			a valid unit
     */
    public TeXFormula addStrut(int type)
    throws InvalidUnitException {
        return add(new SpaceAtom(type));
    }
    
    /**
     * Inserts a strut box (whitespace) with the given width (in widthUnits), height
     * (in heightUnits) and depth (in depthUnits) at the end of the current TeXFormula.
     *
     * @param widthUnit a unit constant used for the width (from {@link TeXConstants})
     * @param width the width of the strut box
     * @param heightUnit a unit constant used for the height (from TeXConstants)
     * @param height the height of the strut box
     * @param depthUnit a unit constant used for the depth (from TeXConstants)
     * @param depth the depth of the strut box
     * @return the modified TeXFormula
     * @throws InvalidUnitException if the given integer value does not represent
     * 			a valid unit
     */
    public TeXFormula addStrut(int widthUnit, float width, int heightUnit,
            float height, int depthUnit, float depth) throws InvalidUnitException {
        return add(new SpaceAtom(widthUnit, width, heightUnit, height, depthUnit,
                depth));
    }
    
   /*
    * Convert this TeXFormula into a box, starting form the given style
    */
    private Box createBox(TeXEnvironment style) {
        if (root == null)
            return new StrutBox(0, 0, 0, 0);
        else
            return root.createBox(style);
    }
        
    /**
     * Creates a TeXIcon from this TeXFormula using the default TeXFont in the given
     * point size and starting from the given TeX style. If the given integer value
     * does not represent a valid TeX style, the default style
     * TeXConstants.STYLE_DISPLAY will be used.
     *
     * @param style a TeX style constant (from {@link TeXConstants}) to start from
     * @param size the default TeXFont's point size
     * @return the created TeXIcon
     */
    public TeXIcon createTeXIcon(int style, float size) {
	TeXEnvironment te = new TeXEnvironment(style, new DefaultTeXFont(size));
	Box box = createBox(te);
	TeXIcon ti = new TeXIcon(box, size);
	ti.isColored = te.isColored;
        return ti;
    }

    /**
     * Changes the background color of the <i>current</i> TeXFormula into the given color.
     * By default, a TeXFormula has no background color, it's transparent.
     * The backgrounds of subformula's will be painted on top of the background of
     * the whole formula! Any changes that will be made to this TeXFormula after this
     * background color was set, will have the default background color (unless it will
     * also be changed into another color afterwards)!
     *
     * @param c the desired background color for the <i>current</i> TeXFormula
     * @return the modified TeXFormula
     */
    public TeXFormula setBackground(Color c) {
        if (c != null) {
            if (root instanceof ColorAtom)
                root = new ColorAtom(c, null, (ColorAtom) root);
            else
                root = new ColorAtom(root, c, null);
        }
        return this;
    }
    
    /**
     * Changes the (foreground) color of the <i>current</i> TeXFormula into the given color.
     * By default, the foreground color of a TeXFormula is the foreground color of the
     * component on which the TeXIcon (created from this TeXFormula) will be painted. The
     * color of subformula's overrides the color of the whole formula.
     * Any changes that will be made to this TeXFormula after this color was set, will be
     * painted in the default color (unless the color will also be changed afterwards into
     * another color)!
     *
     * @param c the desired foreground color for the <i>current</i> TeXFormula
     * @return the modified TeXFormula
     */
    public TeXFormula setColor(Color c) {
        if (c != null) {
            if (root instanceof ColorAtom)
                root = new ColorAtom(null, c, (ColorAtom) root);
            else
                root = new ColorAtom(root, null, c);
        }
        return this;
    }
    
    /**
     * Sets a fixed left and right type of the current TeXFormula. This has an influence
     * on the glue that will be inserted before and after this TeXFormula.
     *
     * @param leftType atom type constant (from {@link TeXConstants})
     * @param rightType atom type constant (from TeXConstants)
     * @return the modified TeXFormula
     * @throws InvalidAtomTypeException if the given integer value does not represent
     * 			a valid atom type
     */
    public TeXFormula setFixedTypes(int leftType, int rightType)
    throws InvalidAtomTypeException {
        root = new TypedAtom(leftType, rightType, root);
        return this;
    }

    /**
     * Get a predefined TeXFormula.
     *
     * @param name the name of the predefined TeXFormula
     * @return a copy of the predefined TeXFormula
     * @throws FormulaNotFoundException if no predefined TeXFormula is found with the
     * 			given name
     */
    public static TeXFormula get(String name) throws FormulaNotFoundException {
        TeXFormula formula = predefinedTeXFormulas.get(name);
        if (formula == null)
            throw new FormulaNotFoundException(name);
        else
            return new TeXFormula(formula);
    }
}