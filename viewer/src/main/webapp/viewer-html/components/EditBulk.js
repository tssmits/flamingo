Ext.define("viewer.components.EditBulk", {
    extend: "viewer.components.Edit",
    lastClickCoords: null,
    selectedFeatures: null,
    originalFormProperties: null,
    config: {
        title: "",
        iconUrl: "",
        tooltip: "",
        layers: null,
        label: "",
        clickRadius: 4,
        allowDelete: false,
        allowCopy: false,
        allowNew: true,
        allowEdit: true,
        cancelOtherControls: ["viewer.components.Edit", "viewer.components.Merge", "viewer.components.Split"],
        formLayout: 'anchor',
        showEditLinkInFeatureInfo: false,
        editHelpText: "",
        isPopup: true,
        rememberValuesInSession:false,
        showSplitButton:false,
        showMergeButton:false,
        showSnappingButton:false,
        details: {
            minWidth: 400,
            minHeight: 250,
            useExtLayout: true
        }
    },
    initAttributeInputs: function (appLayer) {
        viewer.components.EditBulk.superclass.initAttributeInputs.call(this, appLayer);
        this.storeOriginalFormProperties();
        this.initSelectedFeatures();
    },
    storeOriginalFormProperties: function () {
        var me = this;
        me.originalFormProperties = {};
        var attributeNames = me.getAttributeNamesThatAreMappedToFormFields();
        attributeNames.forEach(function (attributeName) {
            var input = me.inputContainer.getForm().findField(attributeName);
            me.originalFormProperties[attributeName] = {
                allowBlank: input.allowBlank,
                emptyText: input.emptyText
            }
        });
    },
    initSelectedFeatures: function () {
        var mapComponent = this.config.viewerController.mapComponent.getMap();
        var attributeNames = this.getAttributeNames();
        this.selectedFeatures = Ext.create('viewer.components.SelectedFeatures', mapComponent, attributeNames);
    },
    getAttributeNames: function () {
        return ['__fid'].concat(this.getAttributeNamesThatAreMappedToFormFields());
    },
    getAttributeNamesThatAreMappedToFormFields: function () {
        var me = this;
        var mapped = [];
        me.appLayer.attributes.forEach(function (attribute) {
            var input = me.inputContainer.getForm().findField(attribute.name);
            if (input !== null) {
                mapped.push(attribute.name);
            }
        });
        return mapped;
    },
    edit: function () {
        viewer.components.EditBulk.superclass.edit.call(this);
        this.selectedFeatures.deselectAll();
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

            this.showAndFocusForm();

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
        var attributeNames = this.getAttributeNamesThatAreMappedToFormFields();
        attributeNames.forEach(function (attributeName) {
            var value = values[attributeName];

            if (typeof value === typeof me.selectedFeatures.MULTIPLE_VALUES) {
                me.makeFormFieldMultiple(attributeName);
            } else {
                me.makeFormFieldScalar(attributeName);
                var input = me.getFormFieldByAttributeName(attributeName);
                input.setValue(value);
            }
        });

        this.formValuesAreBeingUpdated = false;
    },
    makeFormFieldScalar: function (attributeName) {
        var field = this.inputContainer.getForm().findField(attributeName);
        var originalProperties = this.originalFormProperties[attributeName];
        field.setEmptyText(originalProperties.emptyText);
        field.allowBlank = originalProperties.allowBlank;
        field.validate();
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
        var EMPTY = i18next.t('viewer_components_edit_48');
        var MULTIPLE = i18next.t('viewer_components_edit_49');
        var values = this.selectedFeatures.getCombinedValuesForAllSelectedFeatures(attributeName);
        values = values.map(function (value) {
            if (typeof value === 'undefined' || value === null) {
                return EMPTY
            } else {
                return '"' + value + '"';
            }
        });
        return MULTIPLE + ' (' + values.join(', ') + ')';
    },
    getFormFieldByAttributeName: function (attributeName) {
        return this.inputContainer.getForm().findField(attributeName);
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
        me.editingLayer = this.config.viewerController.getLayer(this.layerSelector.getValue());
        var applayerId = me.editingLayer.getId();

        for (var featureIndex = 0; featureIndex < features.length; featureIndex++) {
            var feature = features[featureIndex];

            this.lastUsedValues[applayerId] = feature;

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

            features[featureIndex] = feature;
        }

        var ebf = this.getEditBulkFeature();
        ebf.editbulk(
            me.editingLayer,
            features,
            function (fid) {
                // me.saveSucces(fid);
                // me.config.viewerController.fireEvent(viewer.viewercontroller.controller.Event.ON_EDIT_SUCCESS, me.editingLayer, feature);
                console.debug('success!');
                var msg = i18next.t('viewer_components_edit_34');
                Ext.Msg.alert("Gelukt", msg);
            }, function (error) {
                // me.failed(error);
                console.debug('failed :(');
                var msg = i18next.t('viewer_components_edit_50');
                Ext.Msg.alert("Niet gelukt", msg);
            });

        this.cancel();
    },
    getEditBulkFeature: function () {
        return Ext.create("viewer.EditBulkFeature", {
            viewerController: this.config.viewerController
        });
    },
    cancel: function () {
        viewer.components.EditBulk.superclass.cancel.call(this);
        this.selectedFeatures.deselectAll();
    },
    onFormFieldChange: function (input, newValue, oldValue) {
        var attributeName = input.name;
        this.selectedFeatures.changeValue(attributeName, newValue);
        this.makeFormFieldScalar(attributeName);
    }
});
