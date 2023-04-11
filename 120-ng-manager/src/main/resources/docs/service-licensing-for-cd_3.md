 How Service-based Licensing is Calculated

For every Active Service found, Harness finds the **95th Percentile** of the number of its Service Instances across a period of 30 days.

The active CD License Usage is calculated as follows:

* Every Active Service consumes a **minimum of ONE license**.
* For every additional 20 Service Instances (95th Percentile of Service Instances across a period of 30 days), Harness adds another license for the corresponding Service.

This Service Instances count might or might not reflect the active instances as at the present time. Instead, it might reflect the 95th percentile count over last 30 days.### Example

Here's an example using 4 different Services.



| **Active Service** | **95th Percentile Active Instances** | **Licenses Consumed** |
| --- | --- | --- |
| Service 1 | 0 | 1 |
| Service 2 | 17 | 1 |
| Service 3 | 22 | 2 |
| Service 4 | 41 | 3 |

