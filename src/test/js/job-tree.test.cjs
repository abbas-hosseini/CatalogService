const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const context=vm.createContext({});
vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/job-tree.js'),'utf8'),context);
const {buildJobTree,jobTreePage}=context;
const rows=[{id:1,parentId:null,companyId:1},{id:2,parentId:1,companyId:1},
  {id:3,parentId:2,companyId:1},{id:4,parentId:1,companyId:1},{id:5,parentId:null,companyId:2}];
const ids=page=>Array.from(page.rows,row=>row.id);

test('opening and closing a branch preserves nested expansion state',()=>{
  const tree=buildJobTree(rows),expanded=new Set();
  assert.deepEqual(ids(jobTreePage(tree,expanded,1,12)),[1,5]);
  expanded.add(1);assert.deepEqual(ids(jobTreePage(tree,expanded,1,12)),[1,2,4,5]);
  expanded.add(2);assert.deepEqual(ids(jobTreePage(tree,expanded,1,12)),[1,2,3,4,5]);
  expanded.delete(1);assert.deepEqual(ids(jobTreePage(tree,expanded,1,12)),[1,5]);
  expanded.add(1);assert.deepEqual(ids(jobTreePage(tree,expanded,1,12)),[1,2,3,4,5]);
});
test('pagination never splits a root from its expanded descendants',()=>{
  const tree=buildJobTree(rows),expanded=new Set([1,2]);
  const first=jobTreePage(tree,expanded,1,1),second=jobTreePage(tree,expanded,2,1);
  assert.deepEqual(ids(first),[1,2,3,4]);assert.deepEqual(ids(second),[5]);
  assert.equal(first.pages,2);assert.equal(jobTreePage(tree,expanded,9,1).page,2);
});
test('search retains the full ancestor path and omits unrelated branches',()=>{
  const tree=buildJobTree(rows,new Set([3]));
  const result=jobTreePage(tree,new Set(tree.branchIds),1,12);
  assert.deepEqual(ids(result),[1,2,3]);assert.equal(tree.total,3);
  assert.deepEqual(Array.from(result.rows,row=>row.depth),[0,1,2]);
  assert.equal(buildJobTree(rows,new Set()).total,0);
});
test('company filters do not expose parents from another company',()=>{
  const filtered=[{id:2,parentId:1,companyId:2},{id:3,parentId:2,companyId:2}];
  const tree=buildJobTree(filtered,new Set([3]));
  assert.deepEqual(ids(jobTreePage(tree,new Set(tree.branchIds),1,12)),[2,3]);
  assert.equal(jobTreePage(tree,new Set(),1,12).rows[0].depth,0);
});
test('orphaned and cyclic records remain reachable once without an infinite loop',()=>{
  const input=[{id:1,parentId:2},{id:2,parentId:1},{id:3,parentId:99},{id:4,parentId:4}];
  const tree=buildJobTree(input),result=ids(jobTreePage(tree,new Set(tree.branchIds),1,12));
  assert.equal(new Set(result).size,4);assert.deepEqual(result.sort((a,b)=>a-b),[1,2,3,4]);
});
test('a deep imported branch can be displayed without recursive traversal',()=>{
  const input=Array.from({length:10000},(_,index)=>({id:index+1,parentId:index||null}));
  const tree=buildJobTree(input),result=jobTreePage(tree,new Set(tree.branchIds),1,12);
  assert.equal(result.rows.length,10000);assert.equal(result.pages,1);
});
