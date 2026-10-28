#!/usr/bin/env node
// Regenerates the generated region of docs/api-contract.md from a springdoc
// /v3/api-docs payload. Harvest one from a dev boot with:
//
//   curl http://localhost:8080/v3/api-docs > openapi.json
//   node docs/generate-api-contract.mjs openapi.json
//
// Everything between the BEGIN/END GENERATED markers is replaced; hand-written
// content around the markers is preserved. The public-path set mirrors
// SecurityConfig#permitAll because OpenAPIConfig attaches a global bearerAuth
// requirement to every operation, so per-operation security in the spec is
// always "JWT" and cannot be used as the auth source.
//
// Note: springdoc emits request bodies under application/json but responses
// under */* — both must be consulted.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const specPath = process.argv[2];
if (!specPath) {
  console.error('usage: node docs/generate-api-contract.mjs <openapi.json>');
  process.exit(1);
}

const docPath = join(dirname(fileURLToPath(import.meta.url)), 'api-contract.md');
const BEGIN = '<!-- BEGIN GENERATED FROM /v3/api-docs - do not hand-edit inside this region -->';
const END = '<!-- END GENERATED -->';

const spec = JSON.parse(readFileSync(specPath, 'utf8').replace(/^\uFEFF/, ''));
const doc = readFileSync(docPath, 'utf8');

const start = doc.indexOf(BEGIN);
const end = doc.indexOf(END);
if (start === -1 || end === -1 || end < start) {
  throw new Error(`markers not found in ${docPath}`);
}

const schemas = spec.components.schemas || {};

const PUBLIC = new Set([
  '/auth/login',
  '/auth/refresh',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/users/register',
  '/health',
]);

// Hand-curated notes for fields whose OpenAPI type is too generic to be useful.
const PROP_NOTES = {
  NotificationPreferencesRequest: { typePreferences: 'map of NotificationType name -> boolean' },
  NotificationPreferencesResponse: { typePreferences: 'map of NotificationType name -> boolean' },
};

function refName(schema) {
  return schema && schema.$ref ? schema.$ref.replace('#/components/schemas/', '') : null;
}

function contentSchema(content) {
  if (!content) return null;
  const media = content['application/json'] || content['*/*'] || null;
  return media ? media.schema : null;
}

function inlineType(schema) {
  if (!schema) return '';
  if (schema.type === 'array') {
    const ref = refName(schema.items);
    return ref ? ref + '[]' : inlineType(schema.items) + '[]';
  }
  return schema.type || 'object';
}

function schemaTable(name) {
  const schema = schemas[name];
  if (!schema || !schema.properties) return '';
  const required = new Set(schema.required || []);
  const notes = PROP_NOTES[name] || {};
  const lines = ['| Field | Type | Required |', '| --- | --- | --- |'];
  for (const field of Object.keys(schema.properties)) {
    const prop = schema.properties[field];
    const ref = refName(prop);
    let type = ref || inlineType(prop);
    const hints = [];
    if (ref) {
      if (notes[field]) hints.push(notes[field]);
    } else {
      if (Array.isArray(prop.enum) && prop.enum.length) hints.push('enum: ' + prop.enum.join(' | '));
      if (prop.type === 'string' && prop.format) hints.push(prop.format);
      if (typeof prop.minLength === 'number') hints.push('min ' + prop.minLength);
      if (typeof prop.maxLength === 'number') hints.push('max ' + prop.maxLength);
      if (typeof prop.minimum === 'number') hints.push('min ' + prop.minimum);
      if (typeof prop.maximum === 'number') hints.push('max ' + prop.maximum);
      if (prop.type === 'object' && prop.additionalProperties) {
        hints.push('map of ' + inlineType(prop.additionalProperties));
      }
      if (notes[field]) hints.push(notes[field]);
    }
    if (hints.length) type += ' (' + hints.join('; ') + ')';
    lines.push('| `' + field + '` | ' + type + ' | ' + (required.has(field) ? 'yes' : 'no') + ' |');
  }
  return lines.join('\n') + '\n';
}

// --- endpoint inventory ------------------------------------------------------

const groups = {};
for (const path of Object.keys(spec.paths)) {
  const group = path.split('/')[1];
  (groups[group] = groups[group] || []).push(path);
}

const seenSchemas = new Set();
const inventory = [];

for (const group of Object.keys(groups).sort()) {
  inventory.push('### ' + group, '');
  for (const path of groups[group].sort()) {
    const item = spec.paths[path];
    for (const method of ['get', 'post', 'put', 'delete']) {
      const op = item[method];
      if (!op) continue;
      const auth = PUBLIC.has(path) ? 'public' : 'JWT';
      inventory.push('**' + method.toUpperCase() + ' ' + path + '** - auth: ' + auth);

      for (const param of op.parameters || []) {
        const type = param.schema ? inlineType(param.schema) : 'string';
        const extras = [];
        if (param.schema && param.schema.format) extras.push(param.schema.format);
        if (param.schema && param.schema.default !== undefined) extras.push('default ' + param.schema.default);
        inventory.push('  - `' + param.name + '` (' + param.in + ', ' + type
          + (extras.length ? ' (' + extras.join(', ') + ')' : '')
          + ', required: ' + (param.required ? 'yes' : 'no') + ')');
      }

      const body = op.requestBody && contentSchema(op.requestBody.content);
      if (body) {
        const name = refName(body);
        inventory.push('  - body: `' + (name || 'JSON object') + '`');
        if (name) seenSchemas.add(name);
      }

      for (const [code, resp] of Object.entries(op.responses || {})) {
        if (!/^[23]\d\d$/.test(code)) continue; // error shapes live in Conventions
        const schema = contentSchema(resp && resp.content);
        if (code === '200') {
          const ref = refName(schema);
          if (ref) {
            inventory.push('  - returns: `' + ref + '`');
            seenSchemas.add(ref);
          } else if (schema && schema.type === 'array') {
            const itemRef = refName(schema.items);
            inventory.push(itemRef ? '  - returns: `' + itemRef + '[]`' : '  - returns: JSON array');
            if (itemRef) seenSchemas.add(itemRef);
          } else if (schema && schema.type === 'object') {
            const mapOf = schema.additionalProperties ? ' (map of ' + inlineType(schema.additionalProperties) + ')' : '';
            inventory.push('  - returns: JSON object' + mapOf);
          } else if (schema && schema.type) {
            inventory.push('  - returns: ' + schema.type);
          }
        } else {
          const ref = refName(schema);
          inventory.push('  - also HTTP ' + code + (ref ? ': `' + ref + '`' : ''));
          if (ref) seenSchemas.add(ref);
        }
      }

      inventory.push('');
    }
  }
}

// --- schema tables ------------------------------------------------------------

const schemaSection = ['## Response schemas', ''];
for (const name of Array.from(seenSchemas).sort()) {
  const table = schemaTable(name);
  if (table) schemaSection.push('#### `' + name + '`', '', table, '');
}

// --- splice into the doc ------------------------------------------------------

const generated = inventory.join('\n').trimEnd() + '\n\n' + schemaSection.join('\n').trimEnd();
const updated = doc.slice(0, start + BEGIN.length) + '\n' + generated + '\n' + doc.slice(end);
writeFileSync(docPath, updated, 'utf8');
console.log('regenerated ' + docPath + ' ('
  + Object.keys(spec.paths).length + ' paths, '
  + seenSchemas.size + ' schema tables, '
  + generated.length + ' generated bytes)');
