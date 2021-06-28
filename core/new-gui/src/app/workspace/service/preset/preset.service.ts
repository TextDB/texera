import { Injectable } from '@angular/core';
import * as Ajv from 'ajv';
import { cloneDeep, has, isEqual, merge, pickBy } from 'lodash';
import { NzMessageService } from 'ng-zorro-antd/message';
import { Observable, Subject } from 'rxjs';
import { DictionaryService } from 'src/app/common/service/user/user-dictionary/dictionary.service';
import { asType, isType } from 'src/app/common/util/assert';
import { CustomJSONSchema7 } from '../../types/custom-json-schema.interface';
import { OperatorMetadataService } from '../operator-metadata/operator-metadata.service';
import { WorkflowActionService } from '../workflow-graph/model/workflow-action.service';

/**
 * Preset service enables saving and applying of Presets, which are objects
 * that represent a collection of settings that can be applied all together.
 * The intent is to allow a user to save settings pertaining to some texera object, and reuse them later
 * 
 * Currently this mainly works for Operator properties. EX: for MysqlSource, users may reuse presets of address/port/username/database/table
 * Operator presets are determined by the presence of the 'enable-presets' in the individual properties of each OperatorSchema (see CustomJSONSchema7)
 * This service relies on DictionaryService for storage, which in turn requires the client to be logged in
 * @author Albert Liu
 */

/**
 * determines icon used by NzMessageService (the little notification that shows when a preset is saved)
 * success == green checkmark
 * error == red x-mark
 * warning == yellow !-mark
 * info == blue i-mark
 */
type AlertMessageType = 'success' | 'error' | 'info' | 'warning';

const PresetSchema: CustomJSONSchema7 = {
  type: "object",
  additionalProperties: {
    type: "string"
  }
}

const PresetArraySchema: CustomJSONSchema7 = {
  type: "array",
  items: PresetSchema,
  uniqueItems: true,
}

export type Preset = {[key: string]: string|number|boolean};

export type PresetDictionary = {
  [Key: string]: Preset[]
}
@Injectable({
  providedIn: 'root'
})
export class PresetService {
  private static DICT_PREFIX = 'Preset'; // key prefix when storing data in dictionary service
  private static ajv = new Ajv();
  private static ajvStrip = new Ajv({ useDefaults: true, removeAdditional: true}); // removes extra properties from an object that aren't described by schema
  private static isPreset = PresetService.ajv.compile(PresetSchema);
  private static isPresetArray = PresetService.ajv.compile(PresetArraySchema);

  public readonly applyPresetStream: Observable<{type: string, target: string, preset: Preset}>;
  public readonly savePresetsStream: Observable<{type: string, target: string, presets: Preset[]}>;
  public presetDict: PresetDictionary;
  
  // readyness check is required when using presets. Output of methods is undefined when unready 
  // this is because PresetService uses DictionaryService which initializes asynchronously and also requires readyness check
  public ready: {promise: Promise<boolean>, value: boolean} = {promise: Promise.resolve(false), value: false}; 

  private applyPresetSubject = new Subject<{type: string, target: string, preset: Preset}>(); // event stream for applying presets to a target (usually type "operator" with specific operatorID as target)
  private savePresetSubject = new Subject<{type: string, target: string, presets: Preset[]}>(); // event stream for saving preset[]s to a target (usually type "operator" an operatorType as target)

  constructor(
    private dictionaryService: DictionaryService,
    private messageService: NzMessageService,
    private workflowActionService: WorkflowActionService,
    private operatorMetadataService: OperatorMetadataService,
    ) {
    this.applyPresetStream = this.applyPresetSubject.asObservable();
    this.savePresetsStream = this.savePresetSubject.asObservable();
    this.presetDict = this.getPresetDict();
    this.handleApplyOperatorPresets();
    this.ready = this.dictionaryService.ready;
  }

  /**
   * broadcast applyPreset event, triggering any subscriber actions
   * By default, type "operator" applyPreset events trigger preset being applied to the targeted operator
   * @param type string, usually "operator"
   * @param target string, usually an operatorID
   * @param preset a subset of operator properties that will be applied
   */
  public applyPreset(type: string, target: string, preset: Preset) {
    this.applyPresetSubject.next({type: type, target: target, preset: preset});
  }

  /**
   * broadcast savePresets event and also save preset to presetDict, which is a *view* (in the database sense) of DictionaryService's dictionary that only stores presets
   * @param type string, usually "operator"
   * @param target string, usualy operatorType 
   * @param presets Preset[]
   * @param displayMessage message to display when saving presets
   * @param messageType see AlertMessageType, determines icon used in popup message
   */
  public savePresets(type: string, target: string, presets: Preset[],
    displayMessage?: string|null, messageType: AlertMessageType = 'success') {

    if (presets.length > 0) {
      this.presetDict[`${type}-${target}`] = presets;
    } else {
      delete this.presetDict[`${type}-${target}`];
    }
    this.savePresetSubject.next({type: type, target: target, presets: presets});
    this.displaySavePresetMessage(messageType, displayMessage);
  }

  /**
   * broadcast savePresets event and also save preset to presetDict, which is a *view* (in the database sense) of DictionaryService's dictionary that only stores presets
   * removes originalPreset if it exists
   * adds newPreset to preset[]
   * @param type string, usually "operator"
   * @param target string, usualy operatorType 
   * @param originalPreset preset to remove
   * @param newPreset preset to add
   * @param displayMessage message to display when saving presets
   * @param messageType see AlertMessageType, determines icon used in popup message
   */
  public updatePreset(type: string, target: string, originalPreset: Preset, newPreset: Preset,
    displayMessage?: string|null, messageType: AlertMessageType = 'success') {

    const presets = cloneDeep(this.getPresets(type, target))
      .filter(oldPreset => !isEqual(oldPreset, originalPreset));
    presets.push(newPreset);

    this.savePresets(type, target, presets, displayMessage, messageType);
  }

  /**
   * broadcast savePresets event and also save preset to presetDict, which is a *view* (in the database sense) of DictionaryService's dictionary that only stores presets
   * removes preset if it exists
   * @param type string, usually "operator"
   * @param target string, usualy operatorType 
   * @param preset preset to remove
   * @param displayMessage message to display when saving presets
   * @param messageType see AlertMessageType, determines icon used in popup message
   */
  public deletePreset(type: string, target: string, preset: Preset,
    displayMessage?: string|null, messageType: AlertMessageType = 'error') {

    const presets = cloneDeep(this.getPresets(type, target))
      .filter(oldPreset => !isEqual(oldPreset, preset));

    this.savePresets(type, target, presets, displayMessage, messageType);
  }

  /**
   * get presets from presetDict
   * @param type string, usually "operator"
   * @param target string, usualy operatorType 
   * @returns Preset[]
   */
  public getPresets(type: string, target: string): Readonly<Preset[]> {
    const presets = this.presetDict[`${type}-${target}`] ?? [];
    if (this.isPresetArray(presets)) {
      return presets;
    } else {
      throw new Error(`stored preset data ${presets} is formatted incorrectly`);
    }
  }

  /**
   * extracts preset schema from operator schema and validates a preset with it
   * @param preset
   * @param operatorID 
   * @returns boolean
   */
  public isValidOperatorPreset(preset: Preset, operatorID: string): boolean {
    const presetSchema = PresetService.getOperatorPresetSchema(
      this.operatorMetadataService.getOperatorSchema(
        this.workflowActionService.getTexeraGraph().getOperator(operatorID).operatorType).jsonSchema);
    const fitsSchema = PresetService.ajv.compile(presetSchema)(preset);
    const noEmptyProperties = Object.keys(preset).every(
      (key: string) => !isType(preset[key], 'string') || ((<string> preset[key]).trim()).length > 0);

    return fitsSchema && noEmptyProperties;
  }

  /**
   * extracts preset schema from operator schema and validates a preset with it.
   * also checks if preset exists in presetDict already.
   * @param preset 
   * @param operatorID 
   * @returns boolean
   */
  public isValidNewOperatorPreset(preset: Preset, operatorID: string): boolean {
    const isNewPreset = !this.getPresets('operator', this.workflowActionService.getTexeraGraph().getOperator(operatorID).operatorType)
      .some(existingPreset => isEqual(preset, existingPreset));

    return isNewPreset && this.isValidOperatorPreset(preset, operatorID);
  }

  /**
   * gets a *view* (in the database sense) of DictionaryService dictionaries, that only has Preset[]'s in them
   * Keys are constructed from 'DICT_PREFIX-type-target'
   * @returns 
   */
  private getPresetDict(): PresetDictionary {
    const presetService = this;
    const dict = this.dictionaryService.forceGetUserDictionary();
    return new Proxy({}, {
      get(_, key: string | symbol) {
        if (!isType(key, 'string')) throw Error("Preset entries must have string keys"); // validate key is string
        if (dict[`${PresetService.DICT_PREFIX}-${key}`] === undefined) return undefined  // validate dict entry is defined
        const presets = JSON.parse(dict[`${PresetService.DICT_PREFIX}-${key}`])          // validate dict entry is a preset array
        if (!presetService.isPresetArray(presets)) throw Error(`Expected preset dict to contain a Preset[] but instead got ${presets}`);
        return presets;
      },
      set(_, key: string | symbol, value: any) {
        if (!isType(key, 'string')) throw Error("Preset entries must have string keys"); // validate key is string
        if (!presetService.isPresetArray(value)) throw Error(`Preset entries must have Preset[] values`); // validate value is preset []
        dict[`${PresetService.DICT_PREFIX}-${key}`] = JSON.stringify(value);
        return true;
      },
      deleteProperty(_, key: string) {
        if (!isType(key, 'string')) throw Error("Preset entries must have string keys"); // validate key is string
        delete dict[`${PresetService.DICT_PREFIX}-${key}`];
        return true;
      },
      defineProperty(_, key: string, attr: PropertyDescriptor) {
        if (!isType(key, 'string')) throw Error("Preset entries must have string keys"); // validate key is string
        if (!presetService.isPresetArray(attr.value)) throw Error(`Preset entries must have Preset[] values`); // validate value is preset []
        dict[`${PresetService.DICT_PREFIX}-${key}`] = JSON.stringify(attr.value);
        return true;
      },
      has(_, key: string) {
        return `${PresetService.DICT_PREFIX}-${key}` in dict;
      },
    });
  }

  private isPreset(presets: any[]): presets is Preset[] {
    return asType(PresetService.isPreset(presets), 'boolean');
  }

  private isPresetArray(presets: any[]): presets is Preset[] {
    return asType(PresetService.isPresetArray(presets), 'boolean');
  }

  private displaySavePresetMessage(messageType: AlertMessageType, displayMessage?: string|null) {
    if (displayMessage === null) return; // do not display explicitly null message
    if (displayMessage === undefined) {  // if undefined, display default messages
      switch (messageType) {
        case 'error':
          this.messageService.error('Preset deleted');
          break;
        case 'info':
          throw new Error(`no default save preset info message`);
          // break;
        case 'success':
          this.messageService.success('Preset saved');
          break;
        case 'warning':
          throw new Error(`no default save preset warning message`);
          // break;
      }
    } else { // display explicitly passed message and messageType
      switch (messageType) {
        case 'error':
          this.messageService.error(displayMessage);
          break;
        case 'info':
          this.messageService.info(displayMessage);
          break;
        case 'success':
          this.messageService.success(displayMessage);
          break;
        case 'warning':
          this.messageService.warning(displayMessage);
          break;
      }
    }
  }

  /**
   * when presets are applied, check for operator presets, and apply them using workflowActionService
   * to change operator properties
   */
  private handleApplyOperatorPresets() {
    this.applyPresetStream.subscribe({
      next: (applyEvent) => {
        if ( applyEvent.type === 'operator' && this.workflowActionService.getTexeraGraph().hasOperator(applyEvent.target)) {
          if (this.isValidOperatorPreset(applyEvent.preset, applyEvent.target)) {
            this.workflowActionService.setOperatorProperty(
              applyEvent.target,
              merge(cloneDeep(this.workflowActionService.getTexeraGraph().getOperator(applyEvent.target).operatorProperties),
                applyEvent.preset));
          } else {
            const schema = PresetService.getOperatorPresetSchema(
              this.operatorMetadataService.getOperatorSchema(
                this.workflowActionService.getTexeraGraph().getOperator(applyEvent.target).operatorType).jsonSchema);
            throw new Error(`Error applying preset: preset ${applyEvent.preset} was not a valid preset for ${applyEvent.target} with schema ${schema}`);
          }
        }
      }
    });
  }

  /**
   * get preset schema from operator schema.
   * preset schema is just the operator schema with only properties that have 'enable-presets': true
   * all properties are required
   * @param operatorSchema 
   * @returns preset schema
   */
  public static getOperatorPresetSchema(operatorSchema: CustomJSONSchema7): CustomJSONSchema7 {
    const copy = cloneDeep(operatorSchema);
    if (operatorSchema.properties === undefined) throw new Error(`provided operator schema ${operatorSchema} has no properties`);
    const properties = pickBy(copy.properties, (prop) => has(prop, 'enable-presets') && (prop as any)['enable-presets'] === true);
    if (isEqual(properties, {})) throw new Error(`provided operator schema ${operatorSchema} has no preset properties`);
    return {
      type: 'object',
      properties: properties,
      required: Object.keys(properties),
      additionalProperties: false,
    };
  }

  /**
   * get preset from operator properties if it has a preset schema and a valid preset (all properties are assigned)
   * Throws an error if operatorProperties doesn't have all the properties in the presetSchema, unlike filterOperatorProperties.
   * @param operatorSchema 
   * @param operatorProperties 
   * @returns Preset
   */
  public static getOperatorPreset(operatorSchema: CustomJSONSchema7, operatorProperties: object): Preset {
    const copy = cloneDeep(operatorProperties as Preset);
    const presetSchema = this.getOperatorPresetSchema(operatorSchema);
    const strip = this.ajvStrip.compile(presetSchema); // this validator also removes extra properties that aren't a part of the preset
    const result = strip(copy)
    if (asType(result, 'boolean') === true) return copy; 
    throw new Error(`provided operator properties ${operatorProperties} does not conform to preset schema ${presetSchema}`);
  }

  /**
   * get the subset of operatorProperties that only includes properties that are in its PresetSchema
   * this doesn't always yield a complete preset, unlike getOperatorPreset
   * @param operatorSchema 
   * @param operatorProperties 
   * @returns 
   */
  public static filterOperatorPresetProperties(operatorSchema: CustomJSONSchema7, operatorProperties: object): Preset {
    const copy = cloneDeep(operatorProperties as Preset);
    const presetSchema = this.getOperatorPresetSchema(operatorSchema);
    const strip = this.ajvStrip.compile(presetSchema); // this validator also removes extra properties that aren't a part of the preset
    strip(copy);
    return copy;
  }
}
