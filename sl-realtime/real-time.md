# Manual parsing 

## routes.txt

### Header
Step 1: routes.txt
route_id,        agency_id        ,route_short_name,route_long_name,route_type,route_desc
9011001004300000,14010000000001001,43              ,               ,100       ,Pendeltåg

Route Id: 9011001004300000
100 = Railway Service (general)
101 = High Speed Rail
102 = Long Distance Rail
103 = Inter Regional Rail
106 = Suburban/Pendeltåg — which is arguably what this should be, but 100 is close enough

Step 2: trips.txt For route id: 9011001004300000
>cat trips.txt| grep 9011001004300000 | wc -l
1348
>head -1 trips.txt && grep 9011001004300000 trips.txt | head -1
route_id        ,service_id,trip_id          ,trip_headsign,trip_short_name,direction_id,shape_id           ,samtrafiken_internal_trip_number
9011001004300000,84        ,14010000656749468,             ,               ,1           ,4014010000536512869,2521

Step 3: calendar.txt/calendar_dates.txt
calendar.txt
service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
84        ,0     ,0      ,0        ,0       ,0     ,0       ,0     ,20260413  ,20260617
calendar_dates.txt
service_id,date     ,exception_type
84        ,20260413 ,1
84        ,20260414 ,1
...
########################
So the logic so far is this
Find my selected  line in the routes.txt, note the route_id
--
For that route id, find all the trips in the trips.txt which has my route_id. Now I have found
all the trips which this train does. (1348 of them) each of this trips has a schedule which is called service_id.
--
The schedule (identified by service_id) can be found in calendar_dates.txt for each service_id
#######################

Step 4: stop_times.txt filtered by trip_id
trip_id          ,arrival_time ,departure_time,stop_id         ,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint,pickup_booking_rule_id,drop_off_booking_rule_id
14010000656749468,08:24:00     ,08:24:00      ,9022001006101001,1            ,Västerhaninge,0          ,1            ,0                  ,1        ,                      ,
14010000656749468,08:29:00     ,08:29:00      ,9022001006091001,2            ,Västerhaninge,0          ,0            ,8647.25            ,1,,
14010000656749468,08:35:00     ,08:35:00      ,9022001006081001,3            ,Västerhaninge,0          ,0            ,16983.49           ,1,,
14010000656749468,08:39:00     ,08:40:00      ,9022001006071001,4            ,Västerhaninge,0          ,0            ,22139.79           ,1,,
14010000656749468,08:43:00     ,08:43:00      ,9022001006061001,5            ,Västerhaninge,0          ,0            ,25720              ,1,,
14010000656749468,08:46:00     ,08:46:00      ,9022001006051001,6            ,Västerhaninge,0          ,0            ,28787              ,1,,
14010000656749468,08:50:00     ,08:50:00      ,9022001006041001,7            ,Västerhaninge,0          ,0            ,31821.3            ,1,,
14010000656749468,08:55:00     ,08:56:00      ,9022001006031001,8            ,Västerhaninge,0          ,0            ,36743.17           ,1,,
...
14010000656749468,09:36:00     ,09:36:00      ,9022001006161001,19           ,Västerhaninge,0          ,0            ,71519.32,1,,
14010000656749468,09:39:00     ,09:39:00      ,9022001006171002,20           ,Västerhaninge,1          ,0            ,74168.82,1,,

Step 5: stops.txt
stop_id         ,stop_name     ,stop_lat  ,stop_lon ,location_type,parent_station  ,platform_code
9022001006101001,Bålsta        ,59.567472 ,17.536321,0            ,9021001006101000,3
9022001006171002,Västerhaninge ,59.122924 ,18.102860,0            ,9021001006171000,2
                

Files:
       1278  agency.txt
    2782162  attributions.txt
       3986  booking_rules.txt
*    180053  calendar_dates.txt
*     26140  calendar.txt
        144  feed_info.txt
*     31131  routes.txt
  147310618  shapes.txt
* 139714666  stop_times.txt
*   1457100  stops.txt
    1161219  transfers.txt
*   6025286  trips.txt