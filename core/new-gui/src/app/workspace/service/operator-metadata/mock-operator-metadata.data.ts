import { JSONSchema7 } from 'json-schema';
import { OperatorSchema, OperatorMetadata, GroupInfo } from '../../types/operator-schema.interface';
import { BreakpointSchema } from '../../types/workflow-common.interface';
import { CustomJSONSchema7 } from '../../types/custom-json-schema.interface';


// Exports constants related to operator schema and operator metadata for testing purposes.

export const mockScanSourceSchema: OperatorSchema = {
  operatorType: 'ScanSource',
  additionalMetadata: {
    userFriendlyName: 'Source: Scan',
    operatorDescription: 'Read records from a table one by one',
    operatorGroupName: 'Source',
    inputPorts: [],
    outputPorts: [{}],
  },
  jsonSchema: {
    properties: {
      tableName: { type: 'string', description: 'name of source table', title: 'table name' }
    },
    required: ['tableName'],
    type: 'object'
  }
};

export const mockFileSourceSchema: OperatorSchema = {
  operatorType: 'FileSource',
  jsonSchema: {
    type: 'object',
    properties: {
      fileName: { 'type': 'string', title: 'file name' },
    },
    required: ['fileName']
  },
  additionalMetadata: {
    userFriendlyName: 'Source: File',
    operatorDescription: 'Read the content of one file or multiple files',
    operatorGroupName: 'Source',
    inputPorts: [],
    outputPorts: [{}],
  }
};

export const mockNlpSentimentSchema: OperatorSchema = {
  operatorType: 'NlpSentiment',
  additionalMetadata: {
    userFriendlyName: 'Sentiment Analysis',
    operatorDescription: 'Sentiment analysis based on Stanford NLP package',
    operatorGroupName: 'Analysis',
    inputPorts: [{}],
    outputPorts: [{}],
  },
  jsonSchema: {
    properties: {
      attribute: { type: 'string', title: 'attribute', autofill: 'attributeName', autofillAttributeOnPort: 0 },
      resultAttribute: { type: 'string', title: 'result attribute' }
    },
    required: ['attribute', 'resultAttribute'],
    type: 'object'
  }
};

export const mockKeywordSourceSchema: OperatorSchema = {
  operatorType: 'KeywordSource',
  jsonSchema: {
    type: 'object',
    properties: {
      query: { type: 'string', title: 'query' },
      attributes: {
        type: 'array',
        items: { type: 'string' },
        title: 'attributes',
        autofill: 'attributeNameList',
        autofillAttributeOnPort: 0
      },
      tableName: { type: 'string', title: 'table name' },
      spanListName: { type: 'string', title: 'span list name' }
    },
    required: ['query', 'attributes', 'tableName']
  },
  additionalMetadata: {
    userFriendlyName: 'Source: Keyword',
    operatorDescription: 'Perform an index-based search on a table using a keyword',
    operatorGroupName: 'Analysis',
    inputPorts: [],
    outputPorts: [{}],
  }
};

export const mockKeywordSearchSchema: OperatorSchema = {
  operatorType: 'KeywordMatcher',
  jsonSchema: {
    type: 'object',
    properties: {
      query: { type: 'string', title: 'query' },
      attributes: {
        type: 'array',
        items: { type: 'string' },
        title: 'attributes',
        autofill: 'attributeNameList',
        autofillAttributeOnPort: 0
      },
      spanListName: { type: 'string', title: 'span list name' }
    },
    required: ['query', 'attributes', ]
  },
  additionalMetadata: {
    userFriendlyName: 'Keyword Search',
    operatorDescription: 'Search the documents using a keyword',
    operatorGroupName: 'Analysis',
    inputPorts: [{}],
    outputPorts: [{}],
  }
};

export const mockAggregationSchema: OperatorSchema = {
  operatorType: 'Aggregation',
  jsonSchema: {
    type: 'object',
    properties: {
      listOfAggregations: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            attribute: { type: 'string', title: 'attribute', autofill: 'attributeName', autofillAttributeOnPort: 0 },
            aggregator: {
              type: 'string',
              enum: ['min', 'max', 'average', 'sum', 'count'],
              uniqueItems: true,
              title: 'aggregator'
            },
            resultAttribute: { type: 'string', title: 'result attribute' }
          }
        },
        title: 'list of aggregations'
      }
    },
    required: ['listOfAggregations']
  },
  additionalMetadata: {
    userFriendlyName: 'Aggregation',
    operatorDescription: 'Aggregate one or more columns to find min, max, sum, average, count of the column',
    operatorGroupName: 'Analysis',
    inputPorts: [{}],
    outputPorts: [{}],
  }
};

export const mockViewResultsSchema: OperatorSchema = {
  operatorType: 'ViewResults',
  jsonSchema: {
    properties: {
      limit: {
        default: 10,
        type: 'integer',
        title: 'limit'
      },
      offset: {
        default: 0,
        type: 'integer',
        title: 'offset'
      }
    },
    type: 'object'
  },
  additionalMetadata: {
    userFriendlyName: 'View Results',
    operatorDescription: 'View the results of the workflow',
    operatorGroupName: 'View Results',
    inputPorts: [{}],
    outputPorts: [],
  }
};

export const mockMultiInputOutputSchema: OperatorSchema = {
  operatorType: 'MultiInputOutput',
  jsonSchema: {
    properties: {},
    type: 'object'
  },
  additionalMetadata: {
    userFriendlyName: '3-I/O Mock op',
    operatorDescription: 'Mock operator with 3 inputs and 3 outputs',
    operatorGroupName: 'Analysis',
    inputPorts: [{}, {}, {}],
    outputPorts: [{}, {}, {}],
  }
};

export const mockUnionSchema: OperatorSchema = {
  operatorType: 'Union',
  jsonSchema: {
    properties: {},
    type: 'object'
  },
  additionalMetadata: {
    userFriendlyName: 'Union',
    operatorDescription: 'Union multiple inputs',
    operatorGroupName: 'Analysis',
    inputPorts: [{allowMultiInputs: true}],
    outputPorts: [{}]
  }
};

export const mockOperatorSchemaList: ReadonlyArray<OperatorSchema> = [
  mockScanSourceSchema,
  mockFileSourceSchema,
  mockKeywordSourceSchema,
  mockKeywordSearchSchema,
  mockNlpSentimentSchema,
  mockAggregationSchema,
  mockViewResultsSchema,
  mockMultiInputOutputSchema,
  mockUnionSchema,
];

export const mockOperatorGroup: ReadonlyArray<GroupInfo> = [
  { groupName: 'Source', groupOrder: 1 },
  { groupName: 'Analysis', groupOrder: 2 },
  { groupName: 'View Results', groupOrder: 3 },
];

export const mockOperatorMetaData: OperatorMetadata = {
  operators: mockOperatorSchemaList,
  groups: mockOperatorGroup
};


export const testJsonSchema: CustomJSONSchema7 = {
  properties: {
    attribute: {
      type: 'string',
      title: 'attribute',
      autofill: 'attributeName',
      autofillAttributeOnPort: 0
    },
    resultAttribute: {
      type: 'string',
      title: 'result attribute'
    }
  },
  required: [
    'attribute',
    'resultAttribute'
  ],
  type: 'object'
};

export const mockBreakpointSchema: BreakpointSchema = {
  jsonSchema: {
    type: 'object',
    oneOf: [
      {
        title: 'condition',
        properties: {
          column: {
            type: 'string',
            title: 'column',
          },
          condition: {
            type: 'string',
            enum: ['contains', 'does not contain', '=', '>', '>=', '<', '<=', '!=', ],
            title: 'condition',
          },
          value: {
            type: 'string',
            title: 'value',
          },
        },
        required: ['column', 'condition', 'value']
      },
      {
        title: 'count',
        properties: {
          count: {
            type: 'integer',
            title: 'count',
          },
        },
        required: ['count']
      },
    ]
  }
};

