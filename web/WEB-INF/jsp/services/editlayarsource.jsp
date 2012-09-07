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
        <title>Edit layar source</title>
    </stripes:layout-component>
    <stripes:layout-component name="body">
        <div id="formcontent">
            <p>
                <stripes:errors/>
                <stripes:messages/>
            </p>

            <stripes:form beanclass="nl.b3p.viewer.admin.stripes.LayarSourceActionBean">
                <c:choose>
                    <c:when test="${actionBean.context.eventName == 'edit'}">
                        <stripes:hidden name="layarSource" value="${actionBean.layarSource.id}"/>
                        <h1 id="headertext">Layar bron bewerken</h1>
                        <!-- TODO: better selection for featuretype-->
                        <div style="float: left;width: 100%">
                            <div style="width: 50%; float: left;">
                            <table class="formtable">                      
                                <tr>
                                    <td>Attribuut bron <c:out value="${actionBean.layarSource.featureType.id}"/>:</td>
                                    <td>
                                        <stripes:select name="layarSource.featureType">
                                    <option value="1">Maak uw keuze..</option>
                                    <c:forEach var="f" items="${actionBean.featureTypes}">    
                                        <c:set var="selected" value="" />
                                        <c:if test="${actionBean.layarSource.featureType.id == f.id}">
                                            <c:set var="selected" value=" selected=\"selected\"" />
                                        </c:if>
                                        <option value="${f.id}"${selected}><c:out value="${f.featureSource.name}"/> <c:out value="${f.typeName}"/></option>
                                    </c:forEach>
                                </stripes:select>
                                </td>
                                </tr>
                                <tr>
                                    <td>Layar service <c:out value="${actionBean.layarSource.layarService.id}"/></td>
                                    <td>
                                        <stripes:select name="layarSource.layarService">
                                    <option value="1">Maak uw keuze..</option>                      
                                    <c:forEach var="ls" items="${actionBean.layarServices}">  
                                        <c:set var="selected" value="" />                
                                        <c:if test="${actionBean.layarSource.layarService.id == ls.id}">
                                            <c:set var="selected" value=" selected=\"selected\"" />
                                        </c:if>
                                        <option value="${ls.id}"${selected}><c:out value="${ls.name}"/></option>
                                    </c:forEach>
                                </stripes:select>                                            
                                </td>
                                </tr>  
                                <tr><td colspan="2">Te publiceren velden, geef aan hoe deze gevuld moeten worden per Object. 
                                        Gebruik '[attribuutnaam]' om een waarde van het object te gebruiken</td></tr>                                
                                <tr>
                                    <td>Titel:</td>
                                    <td>
                                        <stripes:text name="layarSource.details['text.title']"></stripes:text>
                                    </td>
                                </tr> 
                                <tr>
                                    <td>Omschrijving:</td>
                                    <td>
                                        <stripes:text name="layarSource.details['text.description']"></stripes:text>
                                    </td>
                                </tr>  
                                <tr>
                                    <td>Footnote</td>
                                    <td>
                                        <stripes:text name="layarSource.details['text.footnote']"></stripes:text>
                                    </td>
                                </tr>                         
                                <tr>
                                    <td>Url</td>
                                    <td>
                                        <stripes:text name="layarSource.details['imageURL']"></stripes:text>
                                    </td>
                                </tr>                                                        
                            </table>
                        </div>
                        <div style="width: 50%;float: left">
                            <!--TODO: some sort of possible attributes for configuring a layer source-->
                        </div>
                        </div>
                        <div class="submitbuttons">
                            <stripes:submit name="save" value="Opslaan"/>
                            <stripes:submit name="cancel" value="Annuleren"/>
                        </div>
                    </c:when>
                    <c:when test="${actionBean.context.eventName == 'save' || actionBean.context.eventName == 'delete'}">
                        <script type="text/javascript">
                            var frameParent = getParent();
                            if(frameParent && frameParent.reloadGrid) {
                                frameParent.reloadGrid();
                            }
                        </script>
                        <stripes:submit name="edit" value="Nieuwe layar bron"/>
                    </c:when>
                    <c:otherwise>
                        <stripes:submit name="edit" value="Nieuwe layar bron"/>
                    </c:otherwise>
                </c:choose>
            </stripes:form>
        </div>
        <script type="text/javascript">
            Ext.onReady(function() {
                appendPanel('headertext', 'formcontent');
            });
        </script>
    </stripes:layout-component>
</stripes:layout-render>
