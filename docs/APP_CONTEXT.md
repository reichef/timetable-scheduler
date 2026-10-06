## Context 
This project realizes a scheduler for time tables to find (pareto) optimal solutions for school timetables.

For this we have to consider 

- Rooms
- Subjects 
- Class levels
- Teachers
- School classes for which time tables have to be created
- The time slots and breaks (Time table)
- Constraints over this information

The scheduler has to find (pareto) optimal solutions so that the time tables for every class is filled as good as possible. 
Further given constraints can weight the importance of school classes. 

The application is written in Kotlin. 
The contraints should be definable by the planners in the school. 
The information should be persisted (e.g., using a database). 
The scheduler should be provided by a web-app where teachers can view the time tables and the planner should be able to generate and edit solutions, presenting the final solution in the end. 

