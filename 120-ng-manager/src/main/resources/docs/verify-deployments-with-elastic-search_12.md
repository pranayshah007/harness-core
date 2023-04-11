### Define a query

1. In the **Query Specifications and Mapping** section, select a log index from the **Log Indexes** list.
   
2. In the **Query** field, enter a log query and select **Run Query** to execute it.
   
    A sample record in the **Records** field. This helps you confirm the accuracy of the query you've constructed.
   
3. In the **Field Mapping** section, map the following identifiers to select the data that you want to be displayed from the logs.
   - **Timestamp Identifier**
   - **Service Instance Identifier**
   - **Message Identifier**
   - **Timestamp Format**

   To define mapping, in each identifier field, do the following:

   1. Select **+**. 
    
      The Select path for Service Instance Identifier page appears.

   2. Go to the identifier value that you want to map and choose **Select**.  
   
      The selected value gets mapped to the corresponding identifier field. 

4. Select **Get sample log messages**.  
   
   Sample logs are displayed that help you verify if the query you built is correct.

