# Assignment 4 Activity 1
## Description
The initial Performer code only has one function for adding strings to an array.

The Operations to Implement:
- Add: Adds a new string to the list of strings.
- Display: Displays the list of strings.
- Count: Returns the number of strings in the list.

## Screencast
- https://youtu.be/l0mjjOSE1sE

## Protocol

### Requests
General Request Format:
```
{ 
   "selected": <int: 1=add, 2=display, 3=count,  0=quit>, 
   "data": <thing to send>
}
```
Fields:
 - selected <int>: The operation selected.
 - data <Depends on the operation>:
   - add <String>: The string to be added.
   - display <None>: None.
   - count <None>: None.
   - quit <None>: None.

### Responses
General Success Response: 
```
{
   "type": <String: "add", "display", "count", "quit">, 
   "data": <thing to return> 
}
```

Fields:
 - type <String>: Echoes original operation selected from request.
 - data <Depends on the operation>: The result returned by the server.
   - Add <String>: Returns the new list 
   - Display <String>: List of strings
   - Count <int>: Number of elements (Strings) in the list
 
General Error Response: 
```
{
   "type": "error", 
   "message"": <error string> 
}
```

## How to run the program
### Terminal
Base Code, please use the following commands:
```
    For Server, run "gradle runTask1 -Pport=8080 -q --console=plain"
    For Threaded Server, run "gradle runTask2 -Pport=8080 -q --console=plain"
    For Threaded Pool Server, run "gradle runTask3 -Pport=8080 -q --console=plain"
```
```   
    For Client, run "gradle runClient -Phost=localhost -Pport=8080 -q --console=plain"
```   



