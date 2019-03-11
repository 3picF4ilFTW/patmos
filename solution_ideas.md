# Comments
* SPM memory blocks must be able to do dual-reads (in same cycle), or a write & read into same cycle. dual writes in same cycle is not needed - the only time where this is needed is when a node requests to write to its own address range, as well as the NI has an external write request. This can **only** happen in the scheduele slot *L*, where a value is written into its memory. For these cases, the local read (from the node into its own memory) can be stalled for 1 clock cycle. Alternatively, the NI could be clocked at double the frequency of the core and NoC. With this, we can do two writes in 1 NoC cycle, and thus do both external writes into the SPM in the NI, as well as internal write from the node.


# Solution approach
* By inversing the schedule, we can respond to read request in the same order as they were received.
* To do this, we must offset the two schedules, to allow for memory reading. Probably one clock cycle.

* We add one more NoC for the return channel. NoC1 supports write and read requests while NoC2 returns the read data. Thereby, NoC1 and NoC2 has the same but inversed and time shifted schedule.

* This, by introducing read and a shared distributed memory, following things must be implemented.

# ToDo
* NI:
    * Handles write/read request. For this, we must extend the package by a flag bit, which indicates a read or write. On a read request, the data packet is empty.
    * Processor, memory and network interfacing.
    * Address to node translation.
    * Choosing time slot for sending message.



# ToDO, sunday edition
* NI: 
    * Make a process with a correlation between node index and schedule counter that gives a valid when in right time slot for transmitting.

* Write in DummyNode

* Test of twoway-mem

![alt tex][logo]


[logo]: TwoWayPictures/Blackboard1.jpg "Logo Title Text 2"
- [Comments](#comments)
- [Solution approach](#solution-approach)
- [ToDo](#todo)
- [ToDO, sunday edition](#todo-sunday-edition)