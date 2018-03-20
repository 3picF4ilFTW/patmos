# Comments
* SPM memory blocks must be able to do dual-reads (in same cycle), or a write & read into same cycle. dual writes in same cycle is not needed - the only time where this is needed is when a node requests to write to its own address range, as well as the NI has an external write request. This can **only** happen in the scheduele slot *L*, where a value is written into its memory. For these cases, the local read (from the node into its own memory) can be stalled for 1 clock cycle. Alternatively, the NI could be clocked at double the frequency of the core and NoC. With this, we can do two writes in 1 NoC cycle, and thus do both external writes into the SPM in the NI, as well as internal write from the node.

## gfdsafdsa
 
### fdsfasd
