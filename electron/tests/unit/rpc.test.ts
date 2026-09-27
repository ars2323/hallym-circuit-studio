/* src/main/rpc.ts: one JSON-RPC object per line; lines out of chunks of any
   size, a character cut between two chunks kept whole. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { encodeRequest, LineReader, parseLine } from '../../src/main/rpc.ts';

test('encodeRequest: JSON-RPC 2.0, one line, params {} when none', () => {
  assert.equal(encodeRequest(7, 'file.new', undefined), '{"jsonrpc":"2.0","id":7,"method":"file.new","params":{}}\n');
  const line = encodeRequest(8, 'file.open', { path: 'C:\\과제\\lab 1.circ' });
  assert.ok(line.endsWith('\n') && !line.slice(0, -1).includes('\n'));
  assert.deepEqual(JSON.parse(line), { jsonrpc: '2.0', id: 8, method: 'file.open', params: { path: 'C:\\과제\\lab 1.circ' } });
});

test('parseLine: a response, an error, a notification', () => {
  assert.deepEqual(parseLine('{"jsonrpc":"2.0","id":3,"result":{"fileId":"f1"}}'), { kind: 'response', id: 3, result: { fileId: 'f1' } });
  assert.deepEqual(parseLine('{"jsonrpc":"2.0","id":4,"result":null}'), { kind: 'response', id: 4, result: null });
  assert.deepEqual(parseLine('{"jsonrpc":"2.0","id":5,"error":{"code":2,"message":"cannot read","data":{"path":"a.circ"}}}'),
    { kind: 'error', id: 5, error: { code: 2, message: 'cannot read', data: { path: 'a.circ' } } });
  assert.deepEqual(parseLine('{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse error"}}'),
    { kind: 'error', id: null, error: { code: -32700, message: 'parse error' } });
  assert.deepEqual(parseLine('{"jsonrpc":"2.0","method":"sim.state","params":{"cycle":2}}'), { kind: 'notification', method: 'sim.state', params: { cycle: 2 } });
});

test('parseLine: anything else is invalid, with the reason', () => {
  assert.equal(parseLine('Picked up JAVA_TOOL_OPTIONS: -Xmx1g').kind, 'invalid');
  assert.equal(parseLine('{"id":1,"result":1}').kind, 'invalid');                       // not 2.0
  assert.equal(parseLine('[1,2]').kind, 'invalid');
  assert.equal(parseLine('{"jsonrpc":"2.0","id":"1","result":1}').kind, 'invalid');     // id not a number
  assert.equal(parseLine('{"jsonrpc":"2.0","id":1,"method":"x"}').kind, 'invalid');     // a request from the engine
  assert.equal(parseLine('{"jsonrpc":"2.0","id":1,"error":{"message":"x"}}').kind, 'invalid');
  assert.equal(parseLine('{"jsonrpc":"2.0","id":1}').kind, 'invalid');
});

test('LineReader: lines across chunks, two in one, \\r\\n, blank lines', () => {
  const r = new LineReader();
  const enc = (s: string) => new TextEncoder().encode(s);
  assert.deepEqual(r.push(enc('{"a":')), []);
  assert.deepEqual(r.push(enc('1}\n{"b":2}\n{"c"')), ['{"a":1}', '{"b":2}']);
  assert.deepEqual(r.push(enc(':3}\r\n\n  \n')), ['{"c":3}']);
  assert.deepEqual(r.end(), []);
});

test('LineReader: a Hangul character cut between two chunks stays whole', () => {
  const r = new LineReader();
  const bytes = new TextEncoder().encode('{"name":"회로"}\n');
  const cut = bytes.indexOf(0xed) + 1; // inside 회 (ED 9A 8C)
  assert.deepEqual(r.push(bytes.subarray(0, cut)), []);
  assert.deepEqual(r.push(bytes.subarray(cut)), ['{"name":"회로"}']);
  // Byte by byte.
  const one = new LineReader();
  const out: string[] = [];
  for (const b of new TextEncoder().encode('{"x":"하람과 하리"}\n{"y":1}\n')) out.push(...one.push(Uint8Array.of(b)));
  assert.deepEqual(out, ['{"x":"하람과 하리"}', '{"y":1}']);
});

test('LineReader: a last line without its newline comes out at the end', () => {
  const r = new LineReader();
  assert.deepEqual(r.push(new TextEncoder().encode('{"a":1}\n{"b":2}')), ['{"a":1}']);
  assert.deepEqual(r.end(), ['{"b":2}']);
});
