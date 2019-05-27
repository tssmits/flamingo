(function (Ext, undefined) {
    Ext.define("viewer.components.EditBulk", {
        extend: "viewer.components.Edit",
        lastClickCoords: null,
        selectedFeatures: null,
        originalFormProperties: null,
        storeOriginalFormProperties: function () {
            var me = this;
            me.originalFormProperties = {};
            var attributeNames = me.getListOfAttributeNamesThatAreMappedToFormFields();
            attributeNames.forEach(function (attributeName) {
                var input = me.inputContainer.getForm().findField(attributeName);
                me.originalFormProperties[attributeName] = {
                    allowBlank: input.allowBlank,
                    emptyText: input.emptyText
                }
            });
        },
        initAttributeInputs: function (appLayer) {
            viewer.components.EditBulk.superclass.initAttributeInputs.call(this, appLayer);
            this.storeOriginalFormProperties();
            this.selectedFeatures = new SelectedFeatures(
                this.config.viewerController.mapComponent.getMap(),
                this.getListOfAttributeNamesThatAreMappedToFormFields()
            );
        },
        makeFormFieldMultiple: function (attributeName) {
            var field = this.inputContainer.getForm().findField(attributeName);
            var placeholder = this.buildPlaceholderForAttributeWithMultipleValues(attributeName);
            field.setEmptyText(placeholder);
            field.allowBlank = true;
            field.setValue('');
            field.validate();
        },
        buildPlaceholderForAttributeWithMultipleValues: function (attributeName) {
            var LEEG = '<leeg>';
            var MEERDERE_WAARDES = 'Meerdere';
            var values = this.selectedFeatures.gatherUniqueValuesForAttributeFromSelectedFeatures(attributeName);
            values = values.map(function (value) {
                if (typeof value === 'undefined' || value === null) {
                    return LEEG
                } else {
                    return '"' + value + '"';
                }
            });
            return MEERDERE_WAARDES + ' (' + values.join(', ') + ')';
        },
        makeFormFieldScalar: function (attributeName) {
            var field = this.inputContainer.getForm().findField(attributeName);
            var originalProperties = this.originalFormProperties[attributeName];
            field.setEmptyText(originalProperties.emptyText);
            field.allowBlank = originalProperties.allowBlank;
            field.validate();
        },
        edit: function () {
            this.selectedFeatures.deselectAll();
            return viewer.components.EditBulk.superclass.edit.call(this);
        },
        mapClicked: function (toolMapClick, comp) {
            if (this.mode === null) {
                return;
            }
            this.showMobilePopup();
            if (this.mode === "new") {
                return;
            }
            this.getContentContainer().mask(i18next.t('viewer_components_edit_16'));
            var coords = comp.coord;
            this.lastClickCoords = coords;
            this.getFeaturesForCoords(coords);
        },
        getFeaturesForCoords: function (coords) {
            return viewer.components.EditBulk.superclass.getFeaturesForCoords.call(this, coords);
        },
        featuresReceived: function (features) {
            return viewer.components.EditBulk.superclass.featuresReceived.call(this, features);
        },
        handleFeature: function (feature) {
            if (feature !== null) {

                if (this.mode === "copy") {
                    this.currentFID = null;
                } else {
                    this.currentFID = feature.__fid;
                }

                if (viewer.components.Component.parseBooleanValue(this.appLayer.details["editfeature.uploadDocument"])) {
                    this.buildUploadedFilesRemovers(feature);
                }
                if (this.geometryEditable) {
                    // var wkt = feature[this.appLayer.geometryAttribute];
                    // var feat = Ext.create("viewer.viewercontroller.controller.Feature", {
                    //     wktgeom: wkt,
                    //     id: "T_0"
                    // });
                    // this.vectorLayer.addFeature(feat);
                    this.showAndFocusForm();
                } else {
                    this.showAndFocusForm();
                }

                this.selectOrDeselectFeature(feature);
            }
            this.getContentContainer().unmask();
        },
        selectOrDeselectFeature: function (feature) {
            if (this.selectedFeatures.isSelected(feature)) {
                this.selectedFeatures.deselect(feature);
            } else {
                this.selectedFeatures.select(feature, this.lastClickCoords);
            }

            if (this.selectedFeatures.zeroSelected()) {
                this.setFormVisible(false);
            }

            if (!this.selectedFeatures.zeroSelected()) {
                this.setFormValues(feature);
            }
        },
        setFormValues: function (feature_arg_will_not_be_used) {
            var me = this;

            this.formValuesAreBeingUpdated = true;

            var values = this.selectedFeatures.getCombinedAttributeValues();
            var attributeNames = this.getListOfAttributeNamesThatAreMappedToFormFields();
            attributeNames.forEach(function(attributeName) {
                var value = values[attributeName];

                if (value === MULTIPLE_VALUES) {
                    me.makeFormFieldMultiple(attributeName);
                } else {
                    me.makeFormFieldScalar(attributeName);
                    var input = me.getFormFieldByAttributeName(attributeName);
                    input.setValue(value);
                }
            });

            this.formValuesAreBeingUpdated = false;
        },
        getFormFieldByAttributeName: function(attributeName) {
            return this.inputContainer.getForm().findField(attributeName);
        },
        cancel: function () {
            this.selectedFeatures.deselectAll();
            return viewer.components.EditBulk.superclass.cancel.call(this);
        },
        save: function () {
            if (this.mode === null) {
                return;
            }

            if (this.mode === "delete") {
                this.remove();
                return;
            }

            if (!this.inputContainer.isValid()) {
                return;
            }

            var me = this;


            var features = this.selectedFeatures.getSelectedFeaturesWithChangesApplied();

            for (var featureIndex = 0; featureIndex < features.length; featureIndex++) {
                var feature = features[featureIndex];
                me.editingLayer = this.config.viewerController.getLayer(this.layerSelector.getValue());
                var applayerId = me.editingLayer.getId();
                this.lastUsedValues [applayerId] = feature;

                if (this.geometryEditable) {
                    if (this.vectorLayer.getActiveFeature()) {
                        var wkt = this.vectorLayer.getActiveFeature().config.wktgeom;
                        feature[this.appLayer.geometryAttribute] = wkt;
                    }
                    if (!feature[this.appLayer.geometryAttribute]) {
                        return;
                    }
                }
                if (this.mode === "edit") {
                    feature.__fid = features[featureIndex].__fid;
                }
                if (this.mode === "copy") {
                    this.currentFID = null;
                    delete feature.__fid;
                }
                try {
                    feature = this.changeFeatureBeforeSave(feature);
                } catch (e) {
                    me.failed(e);
                    return;
                }
                var ef = this.getEditFeature();
                ef.edit(
                    me.editingLayer,
                    feature,
                    function (fid) {
                        // me.saveSucces(fid);
                        // me.config.viewerController.fireEvent(viewer.viewercontroller.controller.Event.ON_EDIT_SUCCESS, me.editingLayer, feature);
                        console.debug('success!');
                    }, function (error) {
                        // me.failed(error);
                        console.debug('failed :(');
                    });
            }

            var msg = i18next.t('viewer_components_edit_34');
            Ext.Msg.alert("Gelukt", msg);
            this.cancel();
        },
        onFormFieldChange: function(me, input, newValue, oldValue) {
            var attributeName = input.name;
            me.selectedFeatures.changeValue(attributeName, newValue);
            me.makeFormFieldScalar(attributeName);
        },
        getListOfAttributeNamesThatAreMappedToFormFields: function () {
            var me = this;
            var mapped = [];
            me.appLayer.attributes.forEach(function(attribute) {
                var input = me.inputContainer.getForm().findField(attribute.name);
                if (input !== null) {
                    mapped.push(attribute.name);
                }
            });
            return mapped;
        }
    });

    function SelectedFeatures(mapComponent, attributeNames) {
        var thisClass = this;
        this.featureItems = [];
        this.mapComponent = mapComponent;
        this.changedValues = {};
        this.attributeNames = attributeNames;

        this.select = function (feature, clickCoord) {
            if (!thisClass.isSelected(feature)) {
                var item = new Item(feature, clickCoord);
                thisClass.featureItems.push(item);
                thisClass.showMarker(feature);
            }
        };

        this.deselect = function (feature) {
            if (thisClass.isSelected(feature)) {
                thisClass.featureItems.splice(getIndex(feature), 1);
                thisClass.hideMarker(feature);

                if (thisClass.zeroSelected()) {
                    this.changedValues = {}
                }
            }
        };

        this.deselectAll = function () {
            var featureItemsCopy = thisClass.featureItems.map( function (item) { return item; });
            for (var i = 0; i < featureItemsCopy.length; i++) {
                thisClass.deselect(featureItemsCopy[i].feature);
            }
        };

        this.changeValue = function (attributeName, newValue) {
            thisClass.changedValues[attributeName] = newValue;
        };

        this.isSelected = function (feature) {
            return -1 !== getIndex(feature);
        };

        this.numSelected = function () {
            return thisClass.featureItems.length;
        };

        this.zeroSelected = function() {
            return 0 === thisClass.featureItems.length;
        };

        this.showMarker = function (feature) {
            var markerId = getMarkerId(feature);
            var item = getItemForFeature(feature);
            thisClass.mapComponent.setMarker(markerId, item.clickCoord.x, item.clickCoord.y, 'default');
        };

        this.hideMarker = function (feature) {
            var markerId = getMarkerId(feature);
            thisClass.mapComponent.removeMarker(markerId);
        };

        this.getSelectedFeatures = function () {
            return thisClass.featureItems.map(function (item) { return shallowCloneObject(item.feature); });
        };

        function shallowCloneObject(original) {
            var clone = {};
            Object.keys(original).forEach(function (key) {
                if (original[key] instanceof Object === true) {
                    clone[key] = original[key];
                } else {
                    clone[key] = JSON.parse(JSON.stringify(original[key]));
                }
            });
            return clone;
        }

        this.getSelectedFeaturesWithChangesApplied = function() {
            return thisClass.getSelectedFeatures().map(function (feature) {
                return thisClass.applyChangesToFeature(feature);
            });
        };

        this.applyChangesToFeature = function (feature) {
            thisClass.getChangedAttributeNames().forEach(function (attributeName) {
                feature[attributeName] = thisClass.changedValues[attributeName];
            });
            return feature;
        };

        this.getChangedAttributeNames = function (){
            return Object.keys(thisClass.changedValues);
        };

        this.getCombinedAttributeValues = function () {
            var allCombinations = {};
            thisClass.getAllAttributeNames().forEach(function (attributeName) {
                allCombinations[attributeName] = thisClass.getScalarRepresentationForAttribute(attributeName);
            });
            return allCombinations;
        };

        this.getAllAttributeNames = function() {
            return Object.keys(this.featureItems[0].feature);
            // return this.attributeNames;
        };

        this.getScalarRepresentationForAttribute = function (attributeName) {
            var values = thisClass.gatherUniqueValuesForAttributeFromSelectedFeatures(attributeName);
            return (values.length === 1) ? values[0] : MULTIPLE_VALUES;
        };

        this.gatherUniqueValuesForAttributeFromSelectedFeatures = function (attributeName) {
            var values = [];
            var features = thisClass.getSelectedFeatures();
            for (var i = 0; i < features.length; i++) {
                var feature = features[i];
                var value = feature[attributeName];
                values.push(value);
            }
            return unique(values);
        };

        function unique(arr) {
            return arr.filter(function (value, index, self) {
                return self.indexOf(value) === index;
            });
        }

        function getIndex(feature) {
            return getFeatureIds().indexOf(getFeatureId(feature));
        }

        function getFeatureIds() {
            return thisClass.featureItems.map(function (item) {
                return getFeatureId(item.feature);
            });
        }

        function getFeatureId(feature) {
            return feature.__fid;
        }

        function getItemForFeature(feature) {
            if (thisClass.isSelected(feature)) {
                return thisClass.featureItems[getIndex(feature)];
            } else {
                return undefined;
            }
        }

        function getMarkerId(feature) {
            return 'selected-feature-marker-' + getFeatureId(feature);
        }

        function Item(feature, clickCoord) {
            this.id = getFeatureId(feature);
            this.feature = shallowCloneObject(feature);
            this.clickCoord = shallowCloneObject(clickCoord);
        }
    }

    function MULTIPLE_VALUES() {
    }

}(Ext));
