/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.Form;
import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;
import org.eclipse.daanse.cwm.model.daanse.notation.PatternText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.notation.Trim;

/**
 * Reads a value from its text and writes the text of a value, as a {@link Notation} says.
 *
 * <p>A reader trims the text as the notation says, reads a missing word as no value ({@code null}), then reads
 * the rest by the form: a {@link TemporalText} gives a value of {@code java.time} of the kind the text holds, a
 * {@link TemporalNumber} an {@link java.time.Instant}, a {@link java.time.LocalDateTime} or a
 * {@link java.time.ZonedDateTime}, a {@link NumberText} a {@link java.math.BigDecimal} (or a {@link Double} for NaN,
 * INF, -INF), a {@link BooleanText} a {@link Boolean}, a {@link PatternText} or no form the text itself. A text that
 * does not fit fails with an {@link UnfitTextException} that names the position and the reason.</p>
 *
 * <p>A writer writes no value as the first missing word ({@code null} where there is none) and refuses a value whose
 * text would be a missing word. Made once per notation, a codec reads and writes any number of values; it is
 * immutable and may be shared between threads.</p>
 */
public final class Codec {

    private final Notation notation;
    private final FormCodec form;
    private final List<String> missing;
    private final Trim trim;

    private Codec(Notation notation, FormCodec form) {
        this.notation = notation;
        this.form = form;
        this.missing = List.copyOf(notation.getMissingWords());
        this.trim = notation.getTrim();
    }

    /**
     * The codec of {@code notation}.
     *
     * @throws IllegalArgumentException      where the form is not well-formed (the rules of
     *                                       {@link NotationModelValidator} say which)
     * @throws UnsupportedOperationException where a well-formed pattern holds fields or symbols this reader does not
     *                                       read
     */
    public static Codec of(Notation notation) {
        Objects.requireNonNull(notation, "notation");
        Form f = notation.getForm();
        FormCodec form = switch (f) {
        case null -> null;
        case TemporalText t -> new TemporalTextCodec(t);
        case TemporalNumber n -> new TemporalNumberCodec(n);
        case NumberText n -> new NumberTextCodec(n);
        case BooleanText b -> new BooleanTextCodec(b);
        case PatternText p -> new PatternTextCodec(p);
        default -> throw new UnsupportedOperationException("no reader for " + f.eClass().getName());
        };
        return new Codec(notation, form);
    }

    /** The notation read and written. */
    public Notation notation() {
        return notation;
    }

    /**
     * The value of {@code text}: {@code null} for {@code null} and for a missing word.
     *
     * @throws UnfitTextException where the text does not fit the form
     */
    public Object read(String text) {
        if (text == null) {
            return null;
        }
        int start = 0;
        int end = text.length();
        if (trim == Trim.BOTH || trim == Trim.START) {
            while (start < end && white(text.charAt(start))) {
                start++;
            }
        }
        if (trim == Trim.BOTH || trim == Trim.END) {
            while (end > start && white(text.charAt(end - 1))) {
                end--;
            }
        }
        String trimmed = text.substring(start, end);
        if (missing.contains(trimmed)) {
            return null;
        }
        if (form == null) {
            return trimmed;
        }
        if (start == end) {
            throw new UnfitTextException(text, start, "the text is empty" + (text.isEmpty() ? "" : " after trimming")
                    + " and no missing word; name \"\" as a missing word to read it as no value");
        }
        return form.read(text, start, end);
    }

    /**
     * The text of {@code value}: the first missing word for {@code null}, or {@code null} where there is none.
     *
     * @throws IllegalArgumentException where the form cannot write the value, or its text would be a missing word
     */
    public String write(Object value) {
        if (value == null) {
            return missing.isEmpty() ? null : missing.get(0);
        }
        String text;
        if (form == null) {
            if (!(value instanceof CharSequence cs)) {
                throw new IllegalArgumentException(value + " is no text, and the notation has no form");
            }
            text = cs.toString();
        } else {
            text = form.write(value);
        }
        if (missing.contains(text)) {
            throw new IllegalArgumentException(value + " writes as '" + text + "', which is a missing word");
        }
        return text;
    }

    /** White space a reader trims: what Java counts as white space or as a space (the no-break space among them). */
    static boolean white(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
