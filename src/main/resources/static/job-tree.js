'use strict';

// Build a display forest after filtering. Missing parents become roots; cycles cannot hide jobs.
function buildJobTree(rows, matchingIds=null) {
  const byId=new Map(rows.map(row=>[row.id,row])),included=new Set();
  for(const id of matchingIds??byId.keys()) {
    let current=id;
    while(byId.has(current)&&!included.has(current)) {
      included.add(current);current=byId.get(current).parentId;
    }
  }
  const children=new Map();
  for(const row of rows)if(included.has(row.id)) {
    if(!children.has(row.parentId))children.set(row.parentId,[]);
    children.get(row.parentId).push(row);
  }
  const roots=[],visited=new Set(),branchIds=[];
  const addRoot=row=>{
    if(visited.has(row.id))return;
    const root={row,children:[]};roots.push(root);visited.add(row.id);
    const stack=[{node:root,index:0}];
    while(stack.length) {
      const frame=stack[stack.length-1],descendants=children.get(frame.node.row.id)||[];
      if(frame.index>=descendants.length) {
        if(frame.node.children.length)branchIds.push(frame.node.row.id);
        stack.pop();continue;
      }
      const child=descendants[frame.index++];
      if(visited.has(child.id))continue;
      visited.add(child.id);
      const node={row:child,children:[]};frame.node.children.push(node);stack.push({node,index:0});
    }
  };
  for(const row of rows)if(included.has(row.id)&&(!included.has(row.parentId)||row.parentId===row.id))addRoot(row);
  for(const row of rows)if(included.has(row.id))addRoot(row);
  return {roots,branchIds,total:included.size};
}

function jobTreePage(tree,expanded,page,pageSize) {
  const pages=Math.max(1,Math.ceil(tree.roots.length/pageSize));
  page=Math.max(1,Math.min(page,pages));
  const rows=[],stack=tree.roots.slice((page-1)*pageSize,page*pageSize).reverse().map(node=>({node,depth:0}));
  while(stack.length) {
    const {node,depth}=stack.pop(),open=expanded.has(node.row.id);
    rows.push({...node.row,depth,treeChildren:node.children.length,treeExpanded:open});
    if(open)for(let i=node.children.length-1;i>=0;i--)stack.push({node:node.children[i],depth:depth+1});
  }
  return {rows,page,pages};
}
