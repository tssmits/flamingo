<%--
Copyright (C) 2011 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<stripes:layout-render name="/WEB-INF/jsp/templates/ext.jsp">
    <stripes:layout-component name="head">
        <title>Edit documenten</title>
    </stripes:layout-component>
    <stripes:layout-component name="body">
        <p>
            <stripes:errors/>
            <stripes:messages/>
        <p>

        <stripes:form beanclass="nl.b3p.viewer.admin.stripes.DocumentActionBean">
            <c:if test="${actionBean.context.eventName == 'editDocument' || actionBean.context.eventName == 'saveDocument'}">
            <h1>bewerken</h1>
                <stripes:hidden name="document" value="${actionBean.document.id}"/>
                <table>
                    <tr>
                        <td>Naam:</td>
                        <td><stripes:text name="document.name" maxlength="255" size="30"/></td>
                    </tr>
                    <tr>
                        <td>Rubriek:</td>
                        <td><stripes:text name="document.category" maxlength="255" size="30"/></td>
                    </tr>
                    <tr>
                        <td>URL:</td>
                        <td><stripes:text name="document.url" maxlength="255" size="30"/></td>
                    </tr>
                </table>

                <stripes:submit name="saveDocument" value="Opslaan"/>
                <stripes:submit name="cancel" value="Annuleren"/>
            </c:if>
            <c:if test="${actionBean.context.eventName == 'cancel'}">
                <stripes:submit name="editDocument" value="Nieuwe document"/>
            </c:if>
        </stripes:form>

    </stripes:layout-component>
</stripes:layout-render>