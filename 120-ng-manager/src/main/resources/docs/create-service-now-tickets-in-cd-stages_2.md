# Limitations

* While it's not a strict limitation, some users can forget that when you use a ServiceNow Create step, it creates a new, independent ServiceNow ticket every time it is run (as opposed to updating the same issue).  
It is important to remember that you should only add ServiceNow Create to a stage if you want to create a new ServiceNow ticket on every run of the stage.
