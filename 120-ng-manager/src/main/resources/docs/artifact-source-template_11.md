# Use artifact templates in stage templates

After creating an artifact source template, you can add it as the artifact source in stage templates.

Using stage templates with artifact source templates is useful for getting your team onboarded quickly.

Artifact sources are used in CD stages. Ensure you are using a CD stage template (**Deploy**).

![cd stage template](static/6cc9d775c2ca99e391bb0deae3fcf40552134bc7f73c4b7069f290bdcea77d68.png)

To add an artifact source template to a stage template, do the following:

1. Open the stage template in Template Studio.
2. In **Service**, in **Select Service**, select **Add Service**.
3. In the new service, in **Artifacts**, select **Use template**.
   
   ![use template](static/8818bc80c6c74f021eb456676680d3c2bb05208b033c045a8adce177ef93af55.png)

4. Select the artifact source template you want to use, and then select **Use Template**.
   
   ![template selected](static/d18f8cfaf6ad2a4e5f61e78d9d9eddff4df602507c8748463eb31c9e2339cb30.png)  

5. In **Configure Artifact Source**, enter a name for the artifact.
6. In **Tag**, you can change the setting to **Fixed Value**, and then select the tag to use. Or you can use a runtime input or expression.
7. Select **Apply Changes**.
   
   The artifact source template is added to the stage template.

   ![added template](static/6d8ee2d4dc0820635512c5112bf5bb6f2ec9f806c1abedf75a71c2a869decbe9.png)  


