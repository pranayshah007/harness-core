 Check if microdnf is installed
if ! command -v microdnf &> /dev/null
then
    echo "microdnf could not be found. Installing..."
    yum install -y microdnf
fi
