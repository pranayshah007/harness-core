### Set Up AWS for Blue/Green Using ELB

To set up AWS for Blue/Green using ELB and Harness, do the following:

1. Ensure you have a Harness Delegate installed on an instance in the same VPC where your ECS cluster and load balancer are installed.
2. In the AWS EC2 console, click **Target Groups**.
  ![](./static/ecs-deployment-tutorial-54.png)
3. In Target Groups, click **Create target group**.
4. Give the target group a name, such as **target1**, and port **8080**.
5. Select the VPC where your ECS cluster instances will be hosted, and click **Create**.
6. Create a second target group using a new name, such as **target2**, use the same port number, **8080**, and the same VPC as the first target.

  It is important that you use the same port numbers for both target groups.When you are done, the target configuration will look something like this:

  ![](./static/ecs-deployment-tutorial-55.png)

  Now that your targets are created, you can create the load balancer that will switch between the targets.
1. Create a Application Load Balancer. In the EC2 Console, click **Load Balancers**.

  ![](./static/ecs-deployment-tutorial-56.png)
2. Click **Create Load Balancer**, and then under **Application Load Balancer**, click **Create**.

  ![](./static/ecs-deployment-tutorial-57.png)

  You do not need to add listeners at this point. We will do that after the load balancer is created.

  Ensure that the VPC you select for the load balancer has two subnets, each in a separate availability zone, like the following:

  ![](./static/ecs-deployment-tutorial-58.png)

  Once your load balancer is created, you can add its Prod and Stage listeners.

1. In your load balancer, click its **Listeners** tab to add the targets you created as listeners.

  ![](./static/ecs-deployment-tutorial-59.png)
2. Click **Add Listener**.
3. In the **Protocol : port** section, enter the port number for your first target, port **80**. Listeners do not need to use the same port numbers as their target groups.
4. In **Default action**, click **Add action**, and select **Forward to**, and then select your target

  ![](./static/ecs-deployment-tutorial-60.png)
5. Click **Save**.
6. Repeat this process to add a listener using the other target you created, using a port number such as **8080**. When you are done you will have two listeners:

![](./static/ecs-deployment-tutorial-61.png)

You AWS ELB setup is complete. Now you can set up you Harness Pipeline.
