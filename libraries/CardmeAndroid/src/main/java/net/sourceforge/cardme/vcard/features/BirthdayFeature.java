package net.sourceforge.cardme.vcard.features;

import java.util.Calendar;
import java.util.Date;

import net.sourceforge.cardme.util.ISOFormat;
import net.sourceforge.cardme.vcard.types.parameters.BirthdayParameterType;

/*
 * Copyright 2011 George El-Haddad. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY GEORGE EL-HADDAD ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL GEORGE EL-HADDAD OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of George El-Haddad.
 */

/**
 * 
 * @author George El-Haddad
 * <br/>
 * Feb 4, 2010
 * 
 * <p><b>RFC 2426</b><br/>
 * <b>3.1.5 BDAY Type Definition</b>
 * <ul>
 * 	<li><b>Type name:</b> BDAY</li>
 * 	<li><b>Type purpose:</b> To specify the birth date of the object the vCard represents.</li>
 * 	<li><b>Type encoding:</b> 8bit</li>
 * 	<li><b>Type value:</b> The default is a single date value. It can also be reset to a single date-time value.</li>
 * 	<li><b>Type special note:</b> None</li>
 * </ul>
 * </p>
 */
public interface BirthdayFeature extends TypeTools {
	
	/**
	 * <p>Returns the birthday.</p>
	 *
	 * @return {@link Calendar}
	 */
	public Calendar getBirthday();
	
	/**
	 * <p>Sets the birth date.</p>
	 *
	 * @param calendar
	 */
	public void setBirthday(Calendar calendar);
	
	/**
	 * <p>Sets the birth date.</p>
	 *
	 * @param date
	 */
	public void setBirthday(Date date);
	
	/**
	 * <p>Sets the birthday parameter type. The birthday
	 * written will be matching the type.</p>
	 *
	 * @see BirthdayParameterType
	 * @param birthdayParamType
	 */
	public void setBirthdayParameterType(BirthdayParameterType birthdayParamType);
	
	/**
	 * <p>Returns the birthday parameter type.</p>
	 *
	 * @return {@link BirthdayParameterType}
	 */
	public BirthdayParameterType getBirthdayParameterType();
	
	/**
	 * <p>Clears the birthday parameter type.</p>
	 *
	 */
	public void clearBirthdayParameterType();
	
	/**
	 * <p>Returns true if a birthday parameter type exists.</p>
	 *
	 * @return boolean
	 */
	public boolean hasBirthdayParameterType();
	
	/**
	 * <p>Set the ISO-8601 format for the output of the birthday.
	 * The default is &quot;UTC Time Extended&quot;.</p>
	 * 
	 * @see ISOFormat
	 */
	public void setISO8601Format(ISOFormat dateTimeEnum);
	
	/**
	 * <p>Returns the proper ISO-8601 date time format
	 * according to the birthday parameter type. Should
	 * it be miss-matching; the default shall be returned
	 * which is <code>ISOFormat.DATE_EXTENDED</code>
	 * for date and <code>ISOFormat.UTC_TIME_EXTENDED</code>
	 * for date-time. Should nothing be set then the default is
	 * to use <code>ISOFormat.UTC_TIME_EXTENDED</code>.
	 *
	 * @return ISOFormat
	 */
	public ISOFormat getISO8601Format();
	
	/**
	 * <p>Returns a full copy of this object.</p>
	 *
	 * @return {@link BirthdayFeature}
	 */
	public BirthdayFeature clone();
}
